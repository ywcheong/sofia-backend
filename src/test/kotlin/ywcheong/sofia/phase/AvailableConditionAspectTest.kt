package ywcheong.sofia.phase

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.config.TestScenarioHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("AvailableCondition Aspect")
class AvailableConditionAspectTest(
    private val mockMvc: MockMvc,
    private val helper: TestScenarioHelper,
    private val systemPhaseService: SystemPhaseService,
) {

    @TestConfiguration
    class TestPhaseControllerConfig {
        @Bean
        fun testPhaseController(): TestPhaseController = TestPhaseController()
    }

    @RestController
    @RequestMapping("/test/phase")
    class TestPhaseController {
        @GetMapping("/deactivation-only")
        @AvailableCondition(phases = [SystemPhase.DEACTIVATION], permissions = [])
        fun deactivationOnly(): Map<String, String> = mapOf("result" to "success")

        @GetMapping("/recruitment-only")
        @AvailableCondition(phases = [SystemPhase.RECRUITMENT], permissions = [])
        fun recruitmentOnly(): Map<String, String> = mapOf("result" to "success")

        @GetMapping("/multiple-phases")
        @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION], permissions = [])
        fun multiplePhases(): Map<String, String> = mapOf("result" to "success")
    }

    @BeforeEach
    fun setUp() {
        helper.setPhase(SystemPhase.DEACTIVATION)
    }

    @Nested
    @DisplayName("@AvailablePhase Aspect 테스트")
    inner class AvailableConditionAspectTests {

        @Test
        fun `현재 단계가 허용된 단계이면 요청이 성공한다`() {
            mockMvc.get("/test/phase/deactivation-only")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                }
        }

        @Test
        fun `현재 단계가 허용되지 않은 단계이면 400을 반환한다`() {
            mockMvc.get("/test/phase/recruitment-only")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `여러 단계가 허용된 경우 현재 단계가 포함되면 성공한다`() {
            helper.setPhase(SystemPhase.RECRUITMENT)

            mockMvc.get("/test/phase/multiple-phases")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                }
        }

        @Test
        fun `여러 단계가 허용된 경우 현재 단계가 포함되지 않으면 400을 반환한다`() {
            mockMvc.get("/test/phase/multiple-phases")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `TRANSLATION 단계에서 RECRUITMENT 전용 기능은 400을 반환한다`() {
            helper.setPhase(SystemPhase.TRANSLATION)

            mockMvc.get("/test/phase/recruitment-only")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `SETTLEMENT 단계에서 TRANSLATION 전용 기능은 400을 반환한다`() {
            helper.setPhase(SystemPhase.SETTLEMENT)

            mockMvc.get("/test/phase/multiple-phases")
                .andExpect { status { isBadRequest() } }
        }
    }

    @Nested
    @DisplayName("동시성 락 테스트")
    inner class ConcurrencyLockTests {

        @Test
        fun `executeIfPhase는 동시에 여러 요청이 가능하다`() {
            val threadCount = 5
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val executor = Executors.newFixedThreadPool(threadCount)

            repeat(threadCount) {
                executor.submit {
                    try {
                        systemPhaseService.executeIfPhase(listOf(SystemPhase.DEACTIVATION)) {
                            successCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            val completed = latch.await(5, TimeUnit.SECONDS)
            executor.shutdown()

            assert(completed) { "타임아웃: Read Lock 동시 접근 실패" }
            assert(successCount.get() == threadCount) { "동시 요청이 모두 성공해야 함: ${successCount.get()}/$threadCount" }
        }

        @Test
        fun `executeIfPhase 중 transitPhase는 대기한다`() {
            val readStartedLatch = CountDownLatch(1)
            val transitCompletedLatch = CountDownLatch(1)
            val readCanFinishLatch = CountDownLatch(1)

            // executeIfPhase 실행 (Read Lock 유지)
            val readThread = thread(start = true) {
                systemPhaseService.executeIfPhase(listOf(SystemPhase.DEACTIVATION)) {
                    readStartedLatch.countDown()
                    readCanFinishLatch.await(5, TimeUnit.SECONDS)
                }
            }

            // transitPhase 시도 (Write Lock 대기)
            val transitThread = thread(start = false) {
                readStartedLatch.await()
                Thread.sleep(200)
                systemPhaseService.transitPhase(SystemPhase.RECRUITMENT)
                transitCompletedLatch.countDown()
            }

            transitThread.start()

            // transitPhase가 대기해야 함
            val transitCompletedDuringRead = transitCompletedLatch.await(500, TimeUnit.MILLISECONDS)

            readCanFinishLatch.countDown()
            readThread.join(3000)
            transitThread.join(3000)

            assert(!transitCompletedDuringRead) { "executeIfPhase 중 transitPhase가 대기하지 않음" }
        }

        @Test
        fun `transitPhase 중 executeIfPhase는 대기한다`() {
            val transitStartedLatch = CountDownLatch(1)
            val readCompletedLatch = CountDownLatch(1)
            val transitCanFinishLatch = CountDownLatch(1)

            // setUp에서 RECRUITMENT로 변경 후 SETTLEMENT로 변경해야 함
            helper.setPhase(SystemPhase.RECRUITMENT)

            // transitPhase 실행 (Write Lock 유지)
            val transitThread = thread(start = true) {
                transitStartedLatch.countDown()
                Thread.sleep(200)
                transitCanFinishLatch.await(5, TimeUnit.SECONDS)
                systemPhaseService.transitPhase(SystemPhase.TRANSLATION)
            }

            // executeIfPhase 시도 (Read Lock 대기)
            val readThread = thread(start = false) {
                transitStartedLatch.await()
                Thread.sleep(100)
                systemPhaseService.executeIfPhase(listOf(SystemPhase.TRANSLATION)) {
                    readCompletedLatch.countDown()
                }
            }

            readThread.start()

            val readCompletedDuringTransit = readCompletedLatch.await(500, TimeUnit.MILLISECONDS)

            transitCanFinishLatch.countDown()
            transitThread.join(3000)
            readThread.join(3000)

            assert(!readCompletedDuringTransit) { "transitPhase 중 executeIfPhase가 대기하지 않음" }
        }
    }
}

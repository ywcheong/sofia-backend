package ywcheong.sofia.phase

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("시스템 페이즈")
class SystemPhaseTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.RECRUITMENT)
    }

    @Nested
    @DisplayName("GET /system-phase - 현재 페이즈 조회")
    inner class GetCurrentPhase {

        @Test
        fun `현재 페이즈를 조회하면 200과 현재 및 다음 페이즈 정보를 반환한다`() {
            // when & then
            mockMvc.get("/system-phase") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("RECRUITMENT") }
                jsonPath("$.currentPhaseDisplayName") { value("모집") }
                jsonPath("$.nextPhase") { value("TRANSLATION") }
                jsonPath("$.nextPhaseDisplayName") { value("번역") }
            }
        }
    }

    @Nested
    @DisplayName("POST /system-phase/transit - 페이즈 전환")
    inner class TransitPhase {

        @Test
        fun `올바른 다음 페이즈로 전환하면 200을 반환한다`() {
            // given
            val request = mapOf(
                "nextPhase" to "TRANSLATION"
            )

            // when & then
            mockMvc.post("/system-phase/transit") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.previousPhase") { value("RECRUITMENT") }
                jsonPath("$.currentPhase") { value("TRANSLATION") }
            }
        }

        @Test
        fun `잘못된 다음 페이즈로 전환하면 400을 반환한다`() {
            // given - 현재 RECRUITMENT에서 DEACTIVATION으로 전환 시도 (순서 위반)
            val request = mapOf(
                "nextPhase" to "DEACTIVATION"
            )

            // when & then
            mockMvc.post("/system-phase/transit") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `동일한 페이즈로 다시 전환하면 400을 반환한다`() {
            // given
            val request = mapOf(
                "nextPhase" to "TRANSLATION"
            )

            // when - 첫 번째 요청 성공
            mockMvc.post("/system-phase/transit") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // when & then - 두 번째 요청 실패 (이미 TRANSLATION이므로)
            mockMvc.post("/system-phase/transit") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        @Transactional
        fun `전체 페이즈 사이클을 순차적으로 전환할 수 있다`() {
            // given - 각 단계별 요청 (RECRUITMENT -> TRANSLATION -> SETTLEMENT -> DEACTIVATION -> RECRUITMENT)
            val phaseSequence = listOf(
                "TRANSLATION" to "번역",
                "SETTLEMENT" to "정산",
                "DEACTIVATION" to "비활성",
                "RECRUITMENT" to "모집",
            )

            // when & then - 전체 사이클 순차 전환
            for ((expectedPhase, expectedDisplayName) in phaseSequence) {
                val request = mapOf("nextPhase" to expectedPhase)

                mockMvc.post("/system-phase/transit") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                    header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.currentPhase") { value(expectedPhase) }
                }

                // 현재 페이즈 확인
                mockMvc.get("/system-phase") {
                    header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.currentPhaseDisplayName") { value(expectedDisplayName) }
                }
            }
        }
    }
}

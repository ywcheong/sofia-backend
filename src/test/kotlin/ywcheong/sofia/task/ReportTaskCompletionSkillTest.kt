package ywcheong.sofia.task

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.kakao.FakeKakaoMessageSimulator
import ywcheong.sofia.phase.SystemPhase

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("과제 완료 보고 스킬")
class ReportTaskCompletionSkillTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    lateinit var simulator: FakeKakaoMessageSimulator

    @BeforeEach
    fun setUp() {
        helper.setupScenario(SystemPhase.TRANSLATION)
        simulator = FakeKakaoMessageSimulator(
            mockMvc = mockMvc,
            objectMapper = objectMapper,
            testScenarioHelper = helper
        )
    }

    @Nested
    @DisplayName("정상적인 완료 보고")
    inner class ReportCompletionSuccess {

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (정상 완료)")
        fun `정상적으로 과제를 완료 보고하면 성공 메시지를 반환한다`() {
            // given: 활성 학생과 할당된 과제
            val user = helper.createActiveStudent("25-001", "홍길동")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "테스트 과제",
                user
            )

            // when: 과제 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "1500"
                )
            )

            // then: 성공 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("번역 보고가 완료되었습니다")) { "성공 메시지가 포함되어야 합니다" }
            assert(response.contains("1500자")) { "총 글자수가 포함되어야 합니다" }
        }

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (글자 수에 '자' 접미사 포함)")
        fun `글자 수에 자가 포함되어도 정상 처리된다`() {
            // given: 활성 학생과 할당된 과제
            val user = helper.createActiveStudent("25-002", "김철수")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "테스트 과제",
                user
            )

            // when: 글자 수에 "자" 접미사가 포함된 상태로 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "2000자"
                )
            )

            // then: 정상 처리
            val response = result.response.contentAsString
            assert(response.contains("번역 보고가 완료되었습니다")) { "성공 메시지가 포함되어야 합니다" }
            assert(response.contains("2000자")) { "총 글자수가 포함되어야 합니다" }
        }

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (글자 수에 공백과 '자' 접미사 포함)")
        fun `글자 수에 공백과 자가 포함되어도 정상 처리된다`() {
            // given: 활성 학생과 할당된 과제
            val user = helper.createActiveStudent("25-003", "박영희")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "테스트 과제",
                user
            )

            // when: 글자 수에 공백과 "자" 접미사가 포함된 상태로 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to " 3000 자 "
                )
            )

            // then: 정상 처리
            val response = result.response.contentAsString
            assert(response.contains("번역 보고가 완료되었습니다")) { "성공 메시지가 포함되어야 합니다" }
            assert(response.contains("3000자")) { "총 글자수가 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("지각 완료 보고")
    inner class LateCompletionReport {

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (지각 완료로 인한 경고 등록)")
        fun `지각 완료 시 사용자에게 경고 개수가 표시된다`() {
            // given: 활성 학생과 49시간 전에 할당된 과제
            val user = helper.createActiveStudent("25-010", "지각사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "지각 과제",
                user
            )
            // 49시간 전으로 할당 시간 설정
            val oldAssignedAt = java.time.Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, oldAssignedAt)

            // when: 과제 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "1000"
                )
            )

            // then: 경고 안내 메시지 포함
            val response = result.response.contentAsString
            assert(response.contains("경고 안내")) { "경고 안내가 포함되어야 합니다" }
            assert(response.contains("늦게 제출")) { "지각 사유가 포함되어야 합니다" }
            assert(response.contains("경고 1회가 등록")) { "경고 등록 안내가 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("오류 케이스")
    inner class ErrorCases {

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (다른 사용자의 과제명 입력)")
        fun `다른 사용자의 과제명이면 에러 메시지를 반환한다`() {
            // given: 활성 학생
            val user = helper.createActiveStudent("25-020", "사용자")

            // when: 존재하지 않는 과제명으로 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to "존재하지 않는 과제명입니다",
                    "characterCount" to "1000"
                )
            )

            // then: 에러 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("존재하지 않는 과제")) { "존재하지 않는 과제 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (이미 완료된 과제)")
        fun `이미 완료된 과제면 에러 메시지를 반환한다`() {
            // given: 활성 학생과 이미 완료된 과제
            val user = helper.createActiveStudent("25-021", "완료사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "이미 완료된 과제",
                user
            )

            // 먼저 과제 완료
            simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "1000"
                )
            )

            // when: 이미 완료된 과제에 다시 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "2000"
                )
            )

            // then: 에러 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("이미 완료된 과제")) { "이미 완료된 과제 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (글자 수 음수 입력)")
        fun `글자 수가 음수면 에러 메시지를 반환한다`() {
            // given: 활성 학생과 할당된 과제
            val user = helper.createActiveStudent("25-022", "음수사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "음수 테스트 과제",
                user
            )

            // when: 음수 글자 수로 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "-100"
                )
            )

            // then: 에러 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("0 이상이어야 합니다")) { "글자 수 음수 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (글자 수 비숫자 입력)")
        fun `글자 수가 숫자가 아니면 에러 메시지를 반환한다`() {
            // given: 활성 학생과 할당된 과제
            val user = helper.createActiveStudent("25-023", "비숫자사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "비숫자 테스트 과제",
                user
            )

            // when: 숫자가 아닌 글자 수로 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "천오백"
                )
            )

            // then: 에러 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("숫자여야 합니다")) { "글자 수 숫자 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (존재하지 않는 과제명 입력)")
        fun `존재하지 않는 과제명이면 에러 메시지를 반환한다`() {
            // given: 활성 학생
            val user = helper.createActiveStudent("25-024", "잘못된과제사용자")

            // when: 존재하지 않는 과제명으로 완료 보고
            val result = simulator.sendMessage(
                fromUser = user,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to "존재하지 않는 과제명입니다",
                    "characterCount" to "1000"
                )
            )

            // then: 에러 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("존재하지 않는 과제")) { "존재하지 않는 과제 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        @DisplayName("POST /kakao/skill - reportwork 요청 (미가입 사용자)")
        fun `미가입 사용자가 요청하면 가입 안내 메시지를 반환한다`() {
            // given: 과제 생성 (다른 사용자)
            val taskOwner = helper.createActiveStudent("25-025", "과제소유자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "테스트 과제",
                taskOwner
            )

            // when: 미가입 사용자가 완료 보고
            val result = simulator.sendMessage(
                fromUser = null,
                action = "reportwork",
                actionData = mapOf(
                    "taskDescription" to task.taskDescription,
                    "characterCount" to "1000"
                )
            )

            // then: 가입 안내 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("가입된 번역버디만")) { "가입 안내 메시지가 포함되어야 합니다" }
        }
    }
}

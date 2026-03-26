package ywcheong.sofia.task

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.RequestHelper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("번역 과제 CRUD")
class TaskCrudTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var requestHelper: RequestHelper
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupTranslationScenario()
        requestHelper = RequestHelper(mockMvc, objectMapper, helper)
    }

    @Nested
    @DisplayName("과제 생성")
    inner class CreateTask {

        @Test
        fun `자동 배정으로 과제를 생성하면 200과 배정된 번역버디 정보를 반환한다`() {
            // given - 활성 번역버디 생성
            val assignee = helper.createActiveStudent("25-001", "홍길동")
            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "테스트 과제",
                "assignmentType" to "AUTOMATIC",
            )

            // when
            val result = requestHelper.post("/tasks", request, adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val taskId = requestHelper.extractUuid(result, "taskId")
            assertThat(taskId).isNotNull()
            assertThat(requestHelper.extractPath(result, "assigneeId")).isEqualTo(assignee.id.toString())
            assertThat(requestHelper.extractPath(result, "assigneeStudentNumber")).isEqualTo("25-001")
            assertThat(requestHelper.extractPath(result, "assigneeName")).isEqualTo("홍길동")
        }

        @Test
        fun `수동 배정으로 과제를 생성하면 200과 지정된 번역버디 정보를 반환한다`() {
            // given - 활성 번역버디 생성
            val assignee = helper.createActiveStudent("25-002", "김철수")
            val request = mapOf(
                "taskType" to "EXTERNAL_POST",
                "taskDescription" to "수동 배정 과제",
                "assignmentType" to "MANUAL",
                "assigneeId" to assignee.id.toString(),
            )

            // when
            val result = requestHelper.post("/tasks", request, adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            assertThat(requestHelper.extractPath(result, "assigneeId")).isEqualTo(assignee.id.toString())
            assertThat(requestHelper.extractPath(result, "assigneeStudentNumber")).isEqualTo("25-002")
            assertThat(requestHelper.extractPath(result, "assigneeName")).isEqualTo("김철수")
        }

        @Test
        fun `수동 배정 시 assigneeId가 없으면 400을 반환한다`() {
            // given
            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "과제",
                "assignmentType" to "MANUAL",
            )

            // when
            val result = requestHelper.post("/tasks", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `과제명이 50자를 초과하면 400을 반환한다`() {
            // given
            val longDescription = "a".repeat(51)
            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to longDescription,
                "assignmentType" to "AUTOMATIC",
            )

            // when
            val result = requestHelper.post("/tasks", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `이미 존재하는 과제를 다시 생성하면 400을 반환한다`() {
            // given - 동일한 과제 생성
            val assignee = helper.createActiveStudent("25-003", "박영희")
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "중복 과제",
                assignee
            )

            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "중복 과제",
                "assignmentType" to "AUTOMATIC",
            )

            // when
            val result = requestHelper.post("/tasks", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `휴식 상태인 번역버디에게 수동 배정하면 400을 반환한다`() {
            // given - 휴식 상태 번역버디 생성
            helper.createActiveStudent("25-003", "다른사용자")
            val restingUser = helper.createActiveStudent("25-004", "휴식중")
            helper.setUserResting(restingUser.id, true)

            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "과제",
                "assignmentType" to "MANUAL",
                "assigneeId" to restingUser.id.toString(),
            )

            // when
            val result = requestHelper.post("/tasks", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `라운드로빈으로 k번 배정하면 서로 다른 k명이 배정된다`() {
            // given - 활성 번역버디 3명 생성
            val user1 = helper.createActiveStudent("25-100", "사용자1")
            val user2 = helper.createActiveStudent("25-101", "사용자2")
            val user3 = helper.createActiveStudent("25-102", "사용자3")

            // when - 3번 자동 배정
            val assigneeIds = (1..3).map {
                val result = requestHelper.post(
                    "/tasks",
                    mapOf(
                        "taskType" to "GAONNURI_POST",
                        "taskDescription" to "과제 $it",
                        "assignmentType" to "AUTOMATIC",
                    ),
                    adminInfo.secretToken
                )
                requestHelper.assertOk(result)
                requestHelper.extractUuid(result, "assigneeId")!!
            }

            // then - 서로 다른 3명이 배정됨
            assertThat(assigneeIds.toSet()).hasSize(3)
            assertThat(assigneeIds).containsExactlyInAnyOrder(user1.id, user2.id, user3.id)
        }

        @Test
        fun `과제 생성 시 배정된 번역버디에게 이메일이 발송된다`() {
            // given - 활성 번역버디 생성
            helper.createActiveStudent("25-200", "이메일테스트")
            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "이메일 발송 테스트 과제",
                "assignmentType" to "AUTOMATIC",
            )

            // when
            val result = requestHelper.post("/tasks", request, adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            helper.assertEmailSent("새로운 번역 과제가 할당되었습니다", "이메일테스트")
            helper.assertEmailSent("새로운 번역 과제가 할당되었습니다", "이메일 발송 테스트 과제")
        }
    }
}

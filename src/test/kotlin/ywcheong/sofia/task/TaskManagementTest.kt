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
@DisplayName("번역 과제 관리")
class TaskManagementTest(
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
    @DisplayName("담당자 변경")
    inner class ChangeAssignee {

        @Test
        fun `미완료 과제의 담당자를 변경하면 200과 새 담당자 정보를 반환한다`() {
            // given
            val originalAssignee = helper.createActiveStudent("25-400", "원래담당자")
            val newAssignee = helper.createActiveStudent("25-401", "새담당자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "담당자 변경 테스트 과제",
                originalAssignee
            )

            val request = mapOf("newAssigneeId" to newAssignee.id.toString())

            // when
            val result = requestHelper.patch("/tasks/${task.id}/assignee", request, adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            assertThat(requestHelper.extractPath(result, "taskId")).isEqualTo(task.id.toString())
            assertThat(requestHelper.extractPath(result, "newAssigneeId")).isEqualTo(newAssignee.id.toString())
            assertThat(requestHelper.extractPath(result, "newAssigneeStudentNumber")).isEqualTo("25-401")
            assertThat(requestHelper.extractPath(result, "newAssigneeName")).isEqualTo("새담당자")
        }

        @Test
        fun `완료된 과제의 담당자를 변경하면 400을 반환한다`() {
            // given
            val originalAssignee = helper.createActiveStudent("25-402", "완료과제담당자")
            val newAssignee = helper.createActiveStudent("25-403", "새담당자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "완료된 과제",
                originalAssignee
            )
            completeTask(task.id, 1000)

            val request = mapOf("newAssigneeId" to newAssignee.id.toString())

            // when
            val result = requestHelper.patch("/tasks/${task.id}/assignee", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `존재하지 않는 과제의 담당자를 변경하면 400을 반환한다`() {
            // given
            val newAssignee = helper.createActiveStudent("25-404", "새담당자")
            val nonExistentTaskId = UUID.randomUUID()

            val request = mapOf("newAssigneeId" to newAssignee.id.toString())

            // when
            val result = requestHelper.patch("/tasks/$nonExistentTaskId/assignee", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `존재하지 않는 사용자로 담당자를 변경하면 400을 반환한다`() {
            // given
            val originalAssignee = helper.createActiveStudent("25-405", "원래담당자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "존재하지 않는 사용자 테스트",
                originalAssignee
            )
            val nonExistentUserId = UUID.randomUUID()

            val request = mapOf("newAssigneeId" to nonExistentUserId.toString())

            // when
            val result = requestHelper.patch("/tasks/${task.id}/assignee", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `휴식 상태인 사용자로 담당자를 변경하면 400을 반환한다`() {
            // given
            val originalAssignee = helper.createActiveStudent("25-406", "원래담당자")
            helper.createActiveStudent("25-407", "다른사용자")
            val restingUser = helper.createActiveStudent("25-408", "휴식중인사용자")
            helper.setUserResting(restingUser.id, true)
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "휴식 사용자 테스트",
                originalAssignee
            )

            val request = mapOf("newAssigneeId" to restingUser.id.toString())

            // when
            val result = requestHelper.patch("/tasks/${task.id}/assignee", request, adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `담당자 변경 시 새 담당자에게 이메일이 발송된다`() {
            // given
            val originalAssignee = helper.createActiveStudent("25-409", "원래담당자")
            val newAssignee = helper.createActiveStudent("25-410", "이메일테스트")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "이메일 발송 테스트 과제",
                originalAssignee
            )

            val request = mapOf("newAssigneeId" to newAssignee.id.toString())

            // when
            val result = requestHelper.patch("/tasks/${task.id}/assignee", request, adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            helper.assertEmailSent("새로운 번역 과제가 할당되었습니다", "이메일테스트")
            helper.assertEmailSent("새로운 번역 과제가 할당되었습니다", "이메일 발송 테스트 과제")
        }
    }

    @Nested
    @DisplayName("과제 삭제")
    inner class DeleteTask {

        @Test
        fun `과제를 삭제하면 204를 반환한다`() {
            // given
            val assignee = helper.createActiveStudent("25-500", "삭제테스트담당자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "삭제할 과제",
                assignee
            )

            // when
            val result = requestHelper.delete("/tasks/${task.id}", adminInfo.secretToken)

            // then
            requestHelper.assertNoContent(result)
        }

        @Test
        fun `삭제된 과제는 목록에서 조회되지 않는다`() {
            // given
            val assignee = helper.createActiveStudent("25-501", "삭제조회테스트")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "삭제 후 조회 테스트",
                assignee
            )

            // when - 삭제
            val deleteResult = requestHelper.delete("/tasks/${task.id}", adminInfo.secretToken)
            requestHelper.assertNoContent(deleteResult)

            // then - 목록에서 해당 과제가 없어야 함
            val listResult = requestHelper.get("/tasks?page=0&size=100", adminInfo.secretToken)
            requestHelper.assertOk(listResult)

            val response = requestHelper.extractJsonNode(listResult)
            val taskIds = response.path("content").map { it.path("id").asText() }
            assertThat(taskIds).doesNotContain(task.id.toString())
        }

        @Test
        fun `존재하지 않는 과제를 삭제하면 400을 반환한다`() {
            // given
            val nonExistentTaskId = UUID.randomUUID()

            // when
            val result = requestHelper.delete("/tasks/$nonExistentTaskId", adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `완료된 과제도 삭제할 수 있다`() {
            // given
            val assignee = helper.createActiveStudent("25-502", "완료과제삭제테스트")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "완료된 삭제 과제",
                assignee
            )
            completeTask(task.id, 1000)

            // when
            val result = requestHelper.delete("/tasks/${task.id}", adminInfo.secretToken)

            // then
            requestHelper.assertNoContent(result)
        }
    }

    // 헬퍼 메서드
    private fun completeTask(taskId: UUID, characterCount: Int) {
        helper.setTaskCompletedAt(taskId, java.time.Instant.now(), characterCount)
    }
}

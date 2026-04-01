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
@DisplayName("번역 과제 조회")
class TaskQueryTest(
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
    @DisplayName("과제 목록 조회")
    inner class FindAllTasks {

        @Test
        fun `페이지네이션으로 과제 목록을 조회하면 200을 반환한다`() {
            // given - 여러 과제 생성
            val user1 = helper.createActiveStudent("25-001", "사용자1")
            val user2 = helper.createActiveStudent("25-002", "사용자2")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제1", user1)
            helper.createTranslationTask(TranslationTask.TaskType.EXTERNAL_POST, "과제2", user2)

            // when
            val result = requestHelper.get("/tasks?page=0&size=10", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("content").isArray).isTrue()
            assertThat(response.path("totalElements").asInt()).isEqualTo(2)
            assertThat(response.path("totalPages").asInt()).isEqualTo(1)
            assertThat(response.path("size").asInt()).isEqualTo(10)
            assertThat(response.path("number").asInt()).isEqualTo(0)
        }

        @Test
        fun `첫 번째 페이지에는 요청한 개수만큼의 과제가 반환된다`() {
            // given - 여러 과제 생성
            val user = helper.createActiveStudent("25-010", "사용자A")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제A", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제B", user)

            // when
            val result = requestHelper.get("/tasks?page=0&size=1", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("content").size()).isEqualTo(1)
            assertThat(response.path("totalElements").asInt()).isEqualTo(2)
        }

        @Test
        fun `과제 응답에 필요한 필드가 모두 포함된다`() {
            // given
            val user = helper.createActiveStudent("25-020", "테스트사용자")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "테스트과제", user)

            // when
            val result = requestHelper.get("/tasks?page=0&size=10", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content").get(0)
            assertThat(content.has("id")).isTrue()
            assertThat(content.has("taskType")).isTrue()
            assertThat(content.has("taskDescription")).isTrue()
            assertThat(content.has("assigneeId")).isTrue()
            assertThat(content.has("assigneeStudentNumber")).isTrue()
            assertThat(content.has("assigneeName")).isTrue()
            assertThat(content.has("assignmentType")).isTrue()
            assertThat(content.has("assignedAt")).isTrue()
            assertThat(content.has("completed")).isTrue()
            assertThat(content.has("late")).isTrue()
        }
    }

    @Nested
    @DisplayName("과제 필터링")
    inner class FilterTasks {

        @Test
        fun `search 파라미터로 과제 설명을 부분 검색할 수 있다`() {
            // given
            val user = helper.createActiveStudent("25-021", "검색테스트사용자")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "가나다라마바사", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "아자차카타파하", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "ABCDEF", user)

            // when - "나다"로 검색
            val result = requestHelper.get("/tasks?page=0&size=10&search=나다", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("totalElements").asInt()).isEqualTo(1)
            assertThat(response.path("content").get(0).path("taskDescription").asText()).isEqualTo("가나다라마바사")
        }

        @Test
        fun `taskType 파라미터로 과제 타입을 필터링할 수 있다`() {
            // given
            val user = helper.createActiveStudent("25-022", "타입필터사용자")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "가온누리 과제", user)
            helper.createTranslationTask(TranslationTask.TaskType.EXTERNAL_POST, "외부 과제", user)

            // when - GAONNURI_POST 타입만 조회
            val result = requestHelper.get("/tasks?page=0&size=10&taskType=GAONNURI_POST", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("totalElements").asInt()).isEqualTo(1)
            assertThat(response.path("content").get(0).path("taskType").asText()).isEqualTo("GAONNURI_POST")
        }

        @Test
        fun `assignmentType 파라미터로 배정 타입을 필터링할 수 있다`() {
            // given
            val user1 = helper.createActiveStudent("25-023", "자동배정사용자")
            val user2 = helper.createActiveStudent("25-024", "수동배정사용자")
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "자동 배정 과제",
                user1,
                TranslationTask.AssignmentType.AUTOMATIC
            )
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "수동 배정 과제",
                user2,
                TranslationTask.AssignmentType.MANUAL
            )

            // when - MANUAL 배정 타입만 조회
            val result = requestHelper.get("/tasks?page=0&size=10&assignmentType=MANUAL", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("totalElements").asInt()).isEqualTo(1)
            assertThat(response.path("content").get(0).path("assignmentType").asText()).isEqualTo("MANUAL")
        }

        @Test
        fun `completed 파라미터로 완료 상태를 필터링할 수 있다`() {
            // given
            val user = helper.createActiveStudent("25-025", "완료필터사용자")
            val completedTask = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "완료된 과제",
                user
            )
            completeTask(completedTask.id, 500)
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "미완료 과제",
                user
            )

            // when - 미완료 과제만 조회
            val result = requestHelper.get("/tasks?page=0&size=10&completed=false", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("totalElements").asInt()).isEqualTo(1)
            assertThat(response.path("content").get(0).path("completed").asBoolean()).isFalse()
            assertThat(response.path("content").get(0).path("taskDescription").asText()).isEqualTo("미완료 과제")
        }

        @Test
        fun `assigneeId 파라미터로 담당자를 필터링할 수 있다`() {
            // given
            val user1 = helper.createActiveStudent("25-026", "담당자1")
            val user2 = helper.createActiveStudent("25-027", "담당자2")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "담당자1 과제", user1)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "담당자2 과제", user2)

            // when - user1이 담당자인 과제만 조회
            val result = requestHelper.get("/tasks?page=0&size=10&assigneeId=${user1.id}", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("totalElements").asInt()).isEqualTo(1)
            assertThat(response.path("content").get(0).path("assigneeId").asText()).isEqualTo(user1.id.toString())
        }

        @Test
        fun `복합 조건으로 필터링할 수 있다`() {
            // given
            val user1 = helper.createActiveStudent("25-028", "복합조건1")
            val user2 = helper.createActiveStudent("25-029", "복합조건2")
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "ABC 자동과제",
                user1,
                TranslationTask.AssignmentType.AUTOMATIC
            )
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "XYZ 수동과제",
                user1,
                TranslationTask.AssignmentType.MANUAL
            )
            val completedTask = helper.createTranslationTask(
                TranslationTask.TaskType.EXTERNAL_POST,
                "외부 ABC",
                user2,
                TranslationTask.AssignmentType.AUTOMATIC
            )
            completeTask(completedTask.id, 500)

            // when - GAONNURI_POST + 미완료 + "ABC" 검색
            val result = requestHelper.get(
                "/tasks?page=0&size=10&taskType=GAONNURI_POST&completed=false&search=ABC",
                adminInfo.secretToken
            )

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("totalElements").asInt()).isEqualTo(1)
            assertThat(response.path("content").get(0).path("taskType").asText()).isEqualTo("GAONNURI_POST")
            assertThat(response.path("content").get(0).path("completed").asBoolean()).isFalse()
            assertThat(response.path("content").get(0).path("taskDescription").asText()).isEqualTo("ABC 자동과제")
        }
    }

    @Nested
    @DisplayName("과제 상태 플래그")
    inner class TaskFlags {

        @Test
        fun `48시간 초과하여 완료된 과제는 late가 true다`() {
            // given - 49시간 전에 할당된 과제를 완료
            val user = helper.createActiveStudent("25-030", "지각과제사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "지각 완료 과제",
                user
            )
            val oldAssignedAt = java.time.Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, oldAssignedAt)
            completeTask(task.id, 500)

            // when
            val result = requestHelper.get("/tasks?page=0&size=10", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content").get(0)
            assertThat(content.path("late").asBoolean()).isTrue()
        }

        @Test
        fun `48시간 미만에 완료된 과제는 late가 false다`() {
            // given - 1시간 전에 할당된 과제를 완료
            val user = helper.createActiveStudent("25-031", "정상과제사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "정상 완료 과제",
                user
            )
            val recentAssignedAt = java.time.Instant.now().minusSeconds(1 * 60 * 60)
            helper.setTaskAssignedAt(task.id, recentAssignedAt)
            completeTask(task.id, 500)

            // when
            val result = requestHelper.get("/tasks?page=0&size=10", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content").get(0)
            assertThat(content.path("late").asBoolean()).isFalse()
        }

        @Test
        fun `미완료 과제는 late가 false다`() {
            // given - createLateTask가 내부적으로 학생도 생성함
            val task = helper.createLateTask("25-031a", "미완료 과제", 1)

            // when
            val result = requestHelper.get("/tasks?page=0&size=10", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content").get(0)
            assertThat(content.path("late").asBoolean()).isFalse()
        }

        @Test
        fun `리마인더 발송된 과제는 remindedAt에 시간값이 있다`() {
            // given - createLateTask가 내부적으로 학생도 생성함
            val task = helper.createLateTask("25-032", "리마인더 발송 과제", 1)
            val remindedAt = java.time.Instant.now().minusSeconds(30 * 60)
            helper.setTaskRemindedAt(task.id, remindedAt)

            // when
            val result = requestHelper.get("/tasks?page=0&size=10", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content").get(0)
            assertThat(content.has("remindedAt")).isTrue()
            assertThat(content.path("remindedAt").isNull).isFalse()
        }
    }

    @Nested
    @DisplayName("과제 정렬")
    inner class SortTasks {

        @Test
        fun `정렬 파라미터 없이 조회하면 기본 동작을 유지한다`() {
            // given - 여러 과제 생성
            val user = helper.createActiveStudent("25-600", "사용자A")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제1", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제2", user)

            // when
            val result = requestHelper.get("/tasks?page=0&size=10", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val response = requestHelper.extractJsonNode(result)
            assertThat(response.path("content").isArray).isTrue()
            assertThat(response.path("totalElements").asInt()).isEqualTo(2)
        }

        @Test
        fun `배정 시간 오름차순 정렬로 조회한다`() {
            // given - 시간 차이를 두고 과제 생성
            val user = helper.createActiveStudent("25-601", "사용자B")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제A", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제B", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제C", user)

            val now = java.time.Instant.now()
            helper.setTaskAssignedAt(task1.id, now.minusSeconds(60 * 60))
            helper.setTaskAssignedAt(task2.id, now.minusSeconds(30 * 60))
            helper.setTaskAssignedAt(task3.id, now.minusSeconds(15 * 60))

            // when
            val result = requestHelper.get("/tasks?page=0&size=10&sortField=assignedAt&sortDirection=ASC", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content")
            assertThat(content.size()).isEqualTo(3)
            assertThat(content.get(0).path("taskDescription").asText()).isEqualTo("과제A")
            assertThat(content.get(1).path("taskDescription").asText()).isEqualTo("과제B")
            assertThat(content.get(2).path("taskDescription").asText()).isEqualTo("과제C")
        }

        @Test
        fun `배정 시간 내림차순 정렬로 조회한다`() {
            // given
            val user = helper.createActiveStudent("25-602", "사용자C")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제D", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제E", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제F", user)

            val now = java.time.Instant.now()
            helper.setTaskAssignedAt(task1.id, now.minusSeconds(60 * 60))
            helper.setTaskAssignedAt(task2.id, now.minusSeconds(30 * 60))
            helper.setTaskAssignedAt(task3.id, now.minusSeconds(15 * 60))

            // when
            val result = requestHelper.get("/tasks?page=0&size=10&sortField=assignedAt&sortDirection=DESC", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content")
            assertThat(content.size()).isEqualTo(3)
            assertThat(content.get(0).path("taskDescription").asText()).isEqualTo("과제F")
            assertThat(content.get(1).path("taskDescription").asText()).isEqualTo("과제E")
            assertThat(content.get(2).path("taskDescription").asText()).isEqualTo("과제D")
        }

        @Test
        fun `완료 시간 오름차순 정렬로 조회한다`() {
            // given
            val user = helper.createActiveStudent("25-603", "사용자D")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제A", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제B", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제C", user)

            val now = java.time.Instant.now()
            completeTask(task1.id, 500)
            helper.setTaskCompletedAt(task1.id, now.minusSeconds(120 * 60), 500)
            completeTask(task2.id, 600)
            helper.setTaskCompletedAt(task2.id, now.minusSeconds(60 * 60), 600)
            completeTask(task3.id, 700)
            helper.setTaskCompletedAt(task3.id, now.minusSeconds(30 * 60), 700)

            // when
            val result = requestHelper.get(
                "/tasks?page=0&size=10&completed=true&sortField=completedAt&sortDirection=ASC",
                adminInfo.secretToken
            )

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content")
            assertThat(content.size()).isEqualTo(3)
            assertThat(content.get(0).path("taskDescription").asText()).isEqualTo("완료과제A")
            assertThat(content.get(1).path("taskDescription").asText()).isEqualTo("완료과제B")
            assertThat(content.get(2).path("taskDescription").asText()).isEqualTo("완료과제C")
        }

        @Test
        fun `완료 시간 내림차순 정렬로 조회한다`() {
            // given
            val user = helper.createActiveStudent("25-604", "사용자E")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제D", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제E", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제F", user)

            val now = java.time.Instant.now()
            completeTask(task1.id, 500)
            helper.setTaskCompletedAt(task1.id, now.minusSeconds(120 * 60), 500)
            completeTask(task2.id, 600)
            helper.setTaskCompletedAt(task2.id, now.minusSeconds(60 * 60), 600)
            completeTask(task3.id, 700)
            helper.setTaskCompletedAt(task3.id, now.minusSeconds(30 * 60), 700)

            // when
            val result = requestHelper.get(
                "/tasks?page=0&size=10&completed=true&sortField=completedAt&sortDirection=DESC",
                adminInfo.secretToken
            )

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content")
            assertThat(content.size()).isEqualTo(3)
            assertThat(content.get(0).path("taskDescription").asText()).isEqualTo("완료과제F")
            assertThat(content.get(1).path("taskDescription").asText()).isEqualTo("완료과제E")
            assertThat(content.get(2).path("taskDescription").asText()).isEqualTo("완료과제D")
        }

        @Test
        fun `글자 수 오름차순 정렬로 조회한다`() {
            // given
            val user = helper.createActiveStudent("25-605", "사용자F")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제A", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제B", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제C", user)

            completeTask(task1.id, 1500)
            completeTask(task2.id, 500)
            completeTask(task3.id, 1000)

            // when
            val result = requestHelper.get(
                "/tasks?page=0&size=10&completed=true&sortField=characterCount&sortDirection=ASC",
                adminInfo.secretToken
            )

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content")
            assertThat(content.size()).isEqualTo(3)
            assertThat(content.get(0).path("taskDescription").asText()).isEqualTo("자수과제B")
            assertThat(content.get(1).path("taskDescription").asText()).isEqualTo("자수과제C")
            assertThat(content.get(2).path("taskDescription").asText()).isEqualTo("자수과제A")
        }

        @Test
        fun `글자 수 내림차순 정렬로 조회한다`() {
            // given
            val user = helper.createActiveStudent("25-606", "사용자G")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제D", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제E", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제F", user)

            completeTask(task1.id, 2000)
            completeTask(task2.id, 800)
            completeTask(task3.id, 1200)

            // when
            val result = requestHelper.get(
                "/tasks?page=0&size=10&completed=true&sortField=characterCount&sortDirection=DESC",
                adminInfo.secretToken
            )

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content")
            assertThat(content.size()).isEqualTo(3)
            assertThat(content.get(0).path("taskDescription").asText()).isEqualTo("자수과제D")
            assertThat(content.get(1).path("taskDescription").asText()).isEqualTo("자수과제F")
            assertThat(content.get(2).path("taskDescription").asText()).isEqualTo("자수과제E")
        }

        @Test
        fun `지원하지 않는 필드로 정렬 요청 시 400 에러를 반환한다`() {
            // given
            helper.createActiveStudent("25-607", "사용자H")

            // when
            val result = requestHelper.get(
                "/tasks?page=0&size=10&sortField=invalidField&sortDirection=ASC",
                adminInfo.secretToken
            )

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `정렬 필드 없이 방향만 지정하면 400 에러를 반환한다`() {
            // given
            helper.createActiveStudent("25-608", "사용자I")

            // when
            val result = requestHelper.get("/tasks?page=0&size=10&sortDirection=DESC", adminInfo.secretToken)

            // then
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `정렬과 조건 검색을 조합한다`() {
            // given
            val user = helper.createActiveStudent("25-609", "사용자J")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료1", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료2", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "미완료", user)

            completeTask(task1.id, 1000)
            completeTask(task2.id, 2000)

            // when
            val result = requestHelper.get(
                "/tasks?page=0&size=10&completed=true&sortField=characterCount&sortDirection=DESC",
                adminInfo.secretToken
            )

            // then
            requestHelper.assertOk(result)
            val content = requestHelper.extractJsonNode(result).path("content")
            assertThat(content.size()).isEqualTo(2)
            assertThat(content.get(0).path("taskDescription").asText()).isEqualTo("완료2")
            assertThat(content.get(1).path("taskDescription").asText()).isEqualTo("완료1")
        }
    }

    @Nested
    @DisplayName("번역 과제 CSV 다운로드")
    inner class DownloadCsv {

        @Test
        fun `관리자가 과제 CSV를 요청하면 200과 CSV 파일을 반환한다`() {
            // when
            val result = requestHelper.get("/tasks/csv", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            assertThat(result.response.getHeader("Content-Type")).isEqualTo("text/csv;charset=UTF-8")
            assertThat(result.response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=tasks.csv")
        }

        @Test
        fun `과제가 있으면 CSV에 과제 정보가 포함된다`() {
            // given
            val student = helper.createActiveStudent("25-051", "홍길동")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "성과 측정용 과제",
                student
            )
            completeTask(task.id, 1000)

            // when
            val result = requestHelper.get("/tasks/csv", adminInfo.secretToken)

            // then
            requestHelper.assertOk(result)
            val csvContent = requestHelper.extractContentAsString(result)
            assertThat(csvContent).contains("과제유형,과제설명,담당자학번,담당자이름,배정유형,배정일시,완료여부,자수,지각여부,리마인드일시")
            assertThat(csvContent).contains("가온누리 게시글")
            assertThat(csvContent).contains("성과 측정용 과제")
            assertThat(csvContent).contains("25-051")
            assertThat(csvContent).contains("홍길동")
            assertThat(csvContent).contains("1000")
            assertThat(csvContent).contains("예") // 완료여부
        }

        @Test
        fun `여러 과제가 있으면 CSV에 모두 포함된다`() {
            // given
            val student1 = helper.createActiveStudent("25-060", "학생1")
            val student2 = helper.createActiveStudent("25-061", "학생2")
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "과제1",
                student1
            )
            helper.createTranslationTask(
                TranslationTask.TaskType.EXTERNAL_POST,
                "과제2",
                student2
            )

            // when
            val result = requestHelper.get("/tasks/csv", adminInfo.secretToken)

            // then
            val csvContent = requestHelper.extractContentAsString(result)
            assertThat(csvContent).contains("가온누리 게시글", "과제1", "25-060", "학생1")
            assertThat(csvContent).contains("외부 게시글", "과제2", "25-061", "학생2")
        }
    }

    // 헬퍼 메서드
    private fun completeTask(taskId: UUID, characterCount: Int) {
        helper.setTaskCompletedAt(taskId, java.time.Instant.now(), characterCount)
    }
}

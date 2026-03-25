package ywcheong.sofia.task

import org.assertj.core.api.Assertions.assertThat
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
import ywcheong.sofia.phase.SystemPhase
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("번역 과제")
class TranslationTaskTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)
        // 관리자를 휴식 상태로 설정 (배정/활성 사용자 대상에서 제외)
        // API를 통하지 않고 직접 설정하여 "마지막 활성 사용자" 검증을 우회
        helper.setUserResting(adminInfo.userId, true)
    }

    @Nested
    @DisplayName("GET /tasks - 과제 목록 조회")
    inner class FindAllTasks {

        @Test
        fun `페이지네이션으로 과제 목록을 조회하면 200을 반환한다`() {
            // given - 여러 과제 생성
            val user1 = helper.createActiveStudent("25-001", "사용자1")
            val user2 = helper.createActiveStudent("25-002", "사용자2")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제1", user1)
            helper.createTranslationTask(TranslationTask.TaskType.EXTERNAL_POST, "과제2", user2)

            // when & then
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.totalPages") { value(1) }
                jsonPath("$.size") { value(10) }
                jsonPath("$.number") { value(0) }
            }
        }

        @Test
        fun `첫 번째 페이지에는 요청한 개수만큼의 과제가 반환된다`() {
            // given - 여러 과제 생성
            val user = helper.createActiveStudent("25-010", "사용자A")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제A", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제B", user)

            // when & then
            mockMvc.get("/tasks?page=0&size=1") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.totalElements") { value(2) }
            }
        }

        @Test
        fun `과제 응답에 필요한 필드가 모두 포함된다`() {
            // given
            val user = helper.createActiveStudent("25-020", "테스트사용자")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "테스트과제", user)

            // when & then
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].id") { isString() }
                jsonPath("$.content[0].taskType") { isString() }
                jsonPath("$.content[0].taskDescription") { isString() }
                jsonPath("$.content[0].assigneeId") { isString() }
                jsonPath("$.content[0].assigneeStudentNumber") { isString() }
                jsonPath("$.content[0].assigneeName") { isString() }
                jsonPath("$.content[0].assignmentType") { isString() }
                jsonPath("$.content[0].assignedAt") { isString() }
                jsonPath("$.content[0].completed") { isBoolean() }
            }
        }
    }

    @Nested
    @DisplayName("POST /tasks - 과제 생성")
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

            // when & then (관리자 권한 필요)
            mockMvc.post("/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.taskId") { isString() }
                jsonPath("$.assigneeId") { value(assignee.id.toString()) }
                jsonPath("$.assigneeStudentNumber") { value("25-001") }
                jsonPath("$.assigneeName") { value("홍길동") }
            }
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

            // when & then (관리자 권한 필요)
            mockMvc.post("/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.taskId") { isString() }
                jsonPath("$.assigneeId") { value(assignee.id.toString()) }
                jsonPath("$.assigneeStudentNumber") { value("25-002") }
                jsonPath("$.assigneeName") { value("김철수") }
            }
        }

        @Test
        fun `수동 배정 시 assigneeId가 없으면 400을 반환한다`() {
            // given
            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "과제",
                "assignmentType" to "MANUAL",
            )

            // when & then
            mockMvc.post("/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
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

            // when & then
            mockMvc.post("/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
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

            // when & then
            mockMvc.post("/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `휴식 상태인 번역버디에게 수동 배정하면 400을 반환한다`() {
            // given - 휴식 상태 번역버디 생성 (마지막 활성 사용자 제약 회피 위해 추가 사용자 생성)
            helper.createActiveStudent("25-003", "다른사용자")
            val restingUser = helper.createActiveStudent("25-004", "휴식중")
            helper.setUserResting(restingUser.id, true)

            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "과제",
                "assignmentType" to "MANUAL",
                "assigneeId" to restingUser.id.toString(),
            )

            // when & then
            mockMvc.post("/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `라운드로빈으로 k번 배정하면 서로 다른 k명이 배정되고 마지막은 작업받지 않은 사용자다`() {
            // given - 활성 번역버디 3명 생성
            val user1 = helper.createActiveStudent("25-100", "사용자1")
            val user2 = helper.createActiveStudent("25-101", "사용자2")
            val user3 = helper.createActiveStudent("25-102", "사용자3")

            // when - 3번 자동 배정
            val assigneeIds = (1..3).map {
                val result = mockMvc.post("/tasks") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(mapOf(
                        "taskType" to "GAONNURI_POST",
                        "taskDescription" to "과제 $it",
                        "assignmentType" to "AUTOMATIC",
                    ))
                    header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
                }.andReturn()

                val response = objectMapper.readTree(result.response.contentAsString)
                UUID.fromString(response.path("assigneeId").asText())
            }

            // then - 서로 다른 3명이 배정됨
            assertThat(assigneeIds.toSet()).hasSize(3)
            assertThat(assigneeIds).containsExactlyInAnyOrder(user1.id, user2.id, user3.id)
        }

        @Test
        fun `과제 생성 시 배정된 번역버디에게 이메일이 발송된다`() {
            // given - 활성 번역버디 생성
            val assignee = helper.createActiveStudent("25-200", "이메일테스트")
            val request = mapOf(
                "taskType" to "GAONNURI_POST",
                "taskDescription" to "이메일 발송 테스트 과제",
                "assignmentType" to "AUTOMATIC",
            )

            // when
            mockMvc.post("/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // then - 과제 할당 이메일이 발송되었는지 검증
            val mailSender = helper.getMailSender()
            val emails = mailSender.getMessagesBySubject("새로운 번역 과제가 할당되었습니다")

            assertThat(emails).hasSize(1)
            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assertThat(emailInfo.content).contains("이메일테스트")
            assertThat(emailInfo.content).contains("이메일 발송 테스트 과제")
        }
    }

    @Nested
    @DisplayName("POST /tasks/{taskId}/completion - 과제 완료 보고")
    inner class ReportCompletion {

        @Test
        fun `과제를 완료 보고하면 200과 taskId를 반환한다`() {
            // given - 과제 생성
            val assignee = helper.createActiveStudent("25-010", "이민수")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "완료 테스트 과제",
                assignee
            )

            val request = mapOf(
                "characterCount" to 1000,
            )

            // when & then (카카오 엔드포인트 권한 필요)
            mockMvc.post("/tasks/${task.id}/completion") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.kakaoAuthHeader())
            }.andExpect {
                status { isOk() }
                jsonPath("$.taskId") { value(task.id.toString()) }
                jsonPath("$.late") { isBoolean() }
            }
        }

        @Test
        fun `존재하지 않는 과제를 완료 보고하면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf(
                "characterCount" to 1000,
            )

            // when & then
            mockMvc.post("/tasks/$nonExistentId/completion") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.kakaoAuthHeader())
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `이미 완료된 과제를 다시 완료 보고하면 400을 반환한다`() {
            // given - 완료된 과제 생성
            val assignee = helper.createActiveStudent("25-011", "최수진")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "이미 완료된 과제",
                assignee
            )
            completeTask(task.id)

            val request = mapOf(
                "characterCount" to 1000,
            )

            // when & then
            mockMvc.post("/tasks/${task.id}/completion") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.kakaoAuthHeader())
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `글자 수가 음수면 400을 반환한다`() {
            // given
            val assignee = helper.createActiveStudent("25-012", "정우성")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "음수 테스트",
                assignee
            )

            val request = mapOf(
                "characterCount" to -100,
            )

            // when & then
            mockMvc.post("/tasks/${task.id}/completion") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.kakaoAuthHeader())
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `지각 완료 시 경고 이메일이 발송된다`() {
            // given - 과제 생성 후 할당 시간을 49시간 전으로 설정 (48시간 초과 = 지각)
            val assignee = helper.createActiveStudent("25-300", "지각테스트")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "지각 완료 테스트 과제",
                assignee
            )
            // 과제 할당 시간을 49시간 전으로 설정
            val lateAssignedAt = java.time.Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, lateAssignedAt)

            val request = mapOf(
                "characterCount" to 1000,
            )

            // when
            mockMvc.post("/tasks/${task.id}/completion") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.kakaoAuthHeader())
            }.andExpect {
                status { isOk() }
                jsonPath("$.late") { value(true) }
            }

            // then - 경고 이메일이 발송되었는지 검증
            val mailSender = helper.getMailSender()
            val emails = mailSender.getMessagesBySubject("번역 과제 경고 발생")

            assertThat(emails).hasSize(1)
            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assertThat(emailInfo.content).contains("지각테스트")
            assertThat(emailInfo.content).contains("지각 완료 테스트 과제")
        }
    }

    // 헬퍼 메서드: 과제 완료 처리 (API 사용)
    private fun completeTask(taskId: UUID) {
        val request = mapOf(
            "characterCount" to 500,
        )
        mockMvc.post("/tasks/$taskId/completion") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", helper.kakaoAuthHeader())
        }.andExpect {
            status { isOk() }
        }
    }

    @Nested
    @DisplayName("GET /tasks/reports/performance.csv - 성과 보고서 생성")
    inner class GeneratePerformanceReport {

        @Test
        fun `관리자가 성과 보고서를 요청하면 200과 CSV 파일을 반환한다`() {
            // when & then
            mockMvc.get("/tasks/reports/performance.csv") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                header { string("Content-Type", "text/csv;charset=UTF-8") }
                header { string("Content-Disposition", "attachment; filename=performance_report.csv") }
            }
        }

        @Test
        fun `완료된 과제가 있으면 CSV에 번역 자수가 포함된다`() {
            // given - 활성 학생과 완료된 과제 생성
            val student = helper.createActiveStudent("25-051", "홍길동")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "성과 측정용 과제",
                student
            )
            completeTask(task.id, 1000)

            // when
            val result = mockMvc.get("/tasks/reports/performance.csv") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andReturn()

            // then
            assertThat(result.response.status).isEqualTo(200)
            val csvContent = result.response.contentAsString
            assertThat(csvContent).contains("학번,이름,번역 자수,보정 자수,경고 횟수,예상 봉사시간(초)")
            assertThat(csvContent).contains("25-051")
            assertThat(csvContent).contains("홍길동")
            assertThat(csvContent).contains("1000")
        }

        @Test
        fun `여러 사용자의 과제가 있으면 CSV에 모두 포함된다`() {
            // given - 여러 활성 학생과 완료된 과제 생성
            val student1 = helper.createActiveStudent("25-060", "학생1")
            val student2 = helper.createActiveStudent("25-061", "학생2")
            val task1 = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "과제1",
                student1
            )
            val task2 = helper.createTranslationTask(
                TranslationTask.TaskType.EXTERNAL_POST,
                "과제2",
                student2
            )
            completeTask(task1.id, 500)
            completeTask(task2.id, 1500)

            // when
            val result = mockMvc.get("/tasks/reports/performance.csv") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andReturn()

            // then
            val csvContent = result.response.contentAsString
            assertThat(csvContent).contains("25-060", "학생1", "500")
            assertThat(csvContent).contains("25-061", "학생2", "1500")
        }
    }

    // 헬퍼 메서드: 과제 완료 처리 (지정된 글자 수)
    private fun completeTask(taskId: UUID, characterCount: Int) {
        val request = mapOf(
            "characterCount" to characterCount,
        )
        mockMvc.post("/tasks/$taskId/completion") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", helper.kakaoAuthHeader())
        }.andExpect {
            status { isOk() }
        }
    }
}

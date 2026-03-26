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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
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
                jsonPath("$.content[0].late") { isBoolean() }
                // remindedAt은 nullable 필드 - 별도 테스트에서 검증
            }
        }

        @Test
        fun `search 파라미터로 과제 설명을 부분 검색할 수 있다`() {
            // given
            val user = helper.createActiveStudent("25-021", "검색테스트사용자")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "가나다라마바사", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "아자차카타파하", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "ABCDEF", user)

            // when & then - "나다"로 검색하면 "가나다라마바사" 과제만 조회
            mockMvc.get("/tasks?page=0&size=10&search=나다") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].taskDescription") { value("가나다라마바사") }
            }
        }

        @Test
        fun `taskType 파라미터로 과제 타입을 필터링할 수 있다`() {
            // given
            val user = helper.createActiveStudent("25-022", "타입필터사용자")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "가온누리 과제", user)
            helper.createTranslationTask(TranslationTask.TaskType.EXTERNAL_POST, "외부 과제", user)

            // when & then - GAONNURI_POST 타입만 조회
            mockMvc.get("/tasks?page=0&size=10&taskType=GAONNURI_POST") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].taskType") { value("GAONNURI_POST") }
            }
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

            // when & then - MANUAL 배정 타입만 조회
            mockMvc.get("/tasks?page=0&size=10&assignmentType=MANUAL") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].assignmentType") { value("MANUAL") }
            }
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

            // when & then - 미완료 과제만 조회
            mockMvc.get("/tasks?page=0&size=10&completed=false") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].completed") { value(false) }
                jsonPath("$.content[0].taskDescription") { value("미완료 과제") }
            }
        }

        @Test
        fun `assigneeId 파라미터로 담당자를 필터링할 수 있다`() {
            // given
            val user1 = helper.createActiveStudent("25-026", "담당자1")
            val user2 = helper.createActiveStudent("25-027", "담당자2")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "담당자1 과제", user1)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "담당자2 과제", user2)

            // when & then - user1이 담당자인 과제만 조회
            mockMvc.get("/tasks?page=0&size=10&assigneeId=${user1.id}") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].assigneeId") { value(user1.id.toString()) }
            }
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

            // when & then - GAONNURI_POST + 미완료 + "ABC" 검색
            mockMvc.get("/tasks?page=0&size=10&taskType=GAONNURI_POST&completed=false&search=ABC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].taskType") { value("GAONNURI_POST") }
                jsonPath("$.content[0].completed") { value(false) }
                jsonPath("$.content[0].taskDescription") { value("ABC 자동과제") }
            }
        }

        @Test
        fun `48시간 초과하여 완료된 과제는 late가 true다`() {
            // given - 49시간 전에 할당된 과제를 완료
            val user = helper.createActiveStudent("25-030", "지각과제사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "지각 완료 과제",
                user
            )
            // 49시간 전으로 할당 시간 설정
            val oldAssignedAt = java.time.Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, oldAssignedAt)
            // 과제 완료
            completeTask(task.id, 500)

            // when & then
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].late") { value(true) }
            }
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
            // 1시간 전으로 할당 시간 설정
            val recentAssignedAt = java.time.Instant.now().minusSeconds(1 * 60 * 60)
            helper.setTaskAssignedAt(task.id, recentAssignedAt)
            // 과제 완료
            completeTask(task.id, 500)

            // when & then
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].late") { value(false) }
            }
        }

        @Test
        fun `미완료 과제는 late가 false다`() {
            // given
            val user = helper.createActiveStudent("25-031a", "미완료사용자")
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "미완료 과제",
                user
            )

            // when & then
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].late") { value(false) }
            }
        }

        @Test
        fun `리마인더 발송된 과제는 remindedAt에 시간값이 있다`() {
            // given
            val user = helper.createActiveStudent("25-032", "리마인더사용자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "리마인더 발송 과제",
                user
            )
            val remindedAt = java.time.Instant.now().minusSeconds(30 * 60)
            helper.setTaskRemindedAt(task.id, remindedAt)

            // when & then
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].remindedAt") { isString() }
            }
        }

        @Test
        fun `리마인더 미발송 과제는 remindedAt이 null이다`() {
            // given
            val user = helper.createActiveStudent("25-033", "미발송사용자")
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "리마인더 미발송 과제",
                user
            )

            // when & then
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].remindedAt") { doesNotExist() }
            }
        }
    }

    @Nested
    @DisplayName("GET /tasks - 정렬 기능")
    inner class SortTasks {

        @Test
        fun `정렬 파라미터 없이 조회하면 기본 동작을 유지한다`() {
            // given - 여러 과제 생성
            val user = helper.createActiveStudent("25-600", "사용자A")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제1", user)
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제2", user)

            // when & then - 정렬 파라미터 없이 조회
            mockMvc.get("/tasks?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { value(2) }
            }
        }

        @Test
        fun `배정 시간 오름차순 정렬로 조회한다`() {
            // given - 시간 차이를 두고 과제 생성
            val user = helper.createActiveStudent("25-601", "사용자B")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제A", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제B", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제C", user)

            // assignedAt을 서로 다른 시간으로 설정
            val now = java.time.Instant.now()
            helper.setTaskAssignedAt(task1.id, now.minusSeconds(60 * 60)) // 1시간 전
            helper.setTaskAssignedAt(task2.id, now.minusSeconds(30 * 60)) // 30분 전
            helper.setTaskAssignedAt(task3.id, now.minusSeconds(15 * 60)) // 15분 전

            // when & then - 배정 시간 오름차순 정렬 (오래된 순)
            mockMvc.get("/tasks?page=0&size=10&sortField=assignedAt&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].taskDescription") { value("과제A") }
                jsonPath("$.content[1].taskDescription") { value("과제B") }
                jsonPath("$.content[2].taskDescription") { value("과제C") }
            }
        }

        @Test
        fun `배정 시간 내림차순 정렬로 조회한다`() {
            // given - 시간 차이를 두고 과제 생성
            val user = helper.createActiveStudent("25-602", "사용자C")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제D", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제E", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제F", user)

            // assignedAt을 서로 다른 시간으로 설정
            val now = java.time.Instant.now()
            helper.setTaskAssignedAt(task1.id, now.minusSeconds(60 * 60)) // 1시간 전
            helper.setTaskAssignedAt(task2.id, now.minusSeconds(30 * 60)) // 30분 전
            helper.setTaskAssignedAt(task3.id, now.minusSeconds(15 * 60)) // 15분 전

            // when & then - 배정 시간 내림차순 정렬 (최신순)
            mockMvc.get("/tasks?page=0&size=10&sortField=assignedAt&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].taskDescription") { value("과제F") }
                jsonPath("$.content[1].taskDescription") { value("과제E") }
                jsonPath("$.content[2].taskDescription") { value("과제D") }
            }
        }

        @Test
        fun `완료 시간 오름차순 정렬로 조회한다`() {
            // given - 완료 시간이 다른 과제들 생성
            val user = helper.createActiveStudent("25-603", "사용자D")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제A", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제B", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제C", user)

            // 각 과제를 완료 처리하되, 완료 시간을 다르게 설정
            val now = java.time.Instant.now()
            completeTask(task1.id, 500)
            helper.setTaskCompletedAt(task1.id, now.minusSeconds(120 * 60), 500) // 2시간 전

            completeTask(task2.id, 600)
            helper.setTaskCompletedAt(task2.id, now.minusSeconds(60 * 60), 600) // 1시간 전

            completeTask(task3.id, 700)
            helper.setTaskCompletedAt(task3.id, now.minusSeconds(30 * 60), 700) // 30분 전

            // when & then - 완료 시간 오름차순 정렬 (오래된 순) + 완료된 과제만
            mockMvc.get("/tasks?page=0&size=10&completed=true&sortField=completedAt&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].taskDescription") { value("완료과제A") }
                jsonPath("$.content[1].taskDescription") { value("완료과제B") }
                jsonPath("$.content[2].taskDescription") { value("완료과제C") }
            }
        }

        @Test
        fun `완료 시간 내림차순 정렬로 조회한다`() {
            // given - 완료 시간이 다른 과제들 생성
            val user = helper.createActiveStudent("25-604", "사용자E")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제D", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제E", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료과제F", user)

            // 각 과제를 완료 처리하되, 완료 시간을 다르게 설정
            val now = java.time.Instant.now()
            completeTask(task1.id, 500)
            helper.setTaskCompletedAt(task1.id, now.minusSeconds(120 * 60), 500) // 2시간 전

            completeTask(task2.id, 600)
            helper.setTaskCompletedAt(task2.id, now.minusSeconds(60 * 60), 600) // 1시간 전

            completeTask(task3.id, 700)
            helper.setTaskCompletedAt(task3.id, now.minusSeconds(30 * 60), 700) // 30분 전

            // when & then - 완료 시간 내림차순 정렬 (최신순) + 완료된 과제만
            mockMvc.get("/tasks?page=0&size=10&completed=true&sortField=completedAt&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].taskDescription") { value("완료과제F") }
                jsonPath("$.content[1].taskDescription") { value("완료과제E") }
                jsonPath("$.content[2].taskDescription") { value("완료과제D") }
            }
        }

        @Test
        fun `글자 수 오름차순 정렬로 조회한다`() {
            // given - 글자 수가 다른 완료된 과제들 생성
            val user = helper.createActiveStudent("25-605", "사용자F")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제A", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제B", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제C", user)

            // 각 과제를 서로 다른 글자 수로 완료 처리
            completeTask(task1.id, 1500)
            completeTask(task2.id, 500)
            completeTask(task3.id, 1000)

            // when & then - 글자 수 오름차순 정렬 + 완료된 과제만
            mockMvc.get("/tasks?page=0&size=10&completed=true&sortField=characterCount&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].taskDescription") { value("자수과제B") } // 500
                jsonPath("$.content[1].taskDescription") { value("자수과제C") } // 1000
                jsonPath("$.content[2].taskDescription") { value("자수과제A") } // 1500
            }
        }

        @Test
        fun `글자 수 내림차순 정렬로 조회한다`() {
            // given - 글자 수가 다른 완료된 과제들 생성
            val user = helper.createActiveStudent("25-606", "사용자G")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제D", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제E", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "자수과제F", user)

            // 각 과제를 서로 다른 글자 수로 완료 처리
            completeTask(task1.id, 2000)
            completeTask(task2.id, 800)
            completeTask(task3.id, 1200)

            // when & then - 글자 수 내림차순 정렬 + 완료된 과제만
            mockMvc.get("/tasks?page=0&size=10&completed=true&sortField=characterCount&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].taskDescription") { value("자수과제D") } // 2000
                jsonPath("$.content[1].taskDescription") { value("자수과제F") } // 1200
                jsonPath("$.content[2].taskDescription") { value("자수과제E") } // 800
            }
        }

        @Test
        fun `지원하지 않는 필드로 정렬 요청 시 400 에러를 반환한다`() {
            // given
            val user = helper.createActiveStudent("25-607", "사용자H")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제", user)

            // when & then - 지원하지 않는 필드로 정렬
            mockMvc.get("/tasks?page=0&size=10&sortField=invalidField&sortDirection=ASC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `정렬 필드 없이 방향만 지정하면 400 에러를 반환한다`() {
            // given
            val user = helper.createActiveStudent("25-608", "사용자I")
            helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "과제", user)

            // when & then - 정렬 필드 없이 방향만 지정
            mockMvc.get("/tasks?page=0&size=10&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `정렬과 조건 검색을 조합한다 - 완료된 과제 중 글자 수 내림차순`() {
            // given - 완료/미완료 혼재
            val user = helper.createActiveStudent("25-609", "사용자J")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료1", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "완료2", user)
            val task3 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "미완료", user)

            completeTask(task1.id, 1000)
            completeTask(task2.id, 2000)
            // task3은 미완료

            // when & then - 완료된 과제만 + 글자 수 내림차순 정렬
            mockMvc.get("/tasks?page=0&size=10&completed=true&sortField=characterCount&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.content[0].taskDescription") { value("완료2") } // 2000
                jsonPath("$.content[1].taskDescription") { value("완료1") } // 1000
            }
        }

        @Test
        fun `정렬과 과제 타입 필터를 조합한다`() {
            // given - 여러 타입의 과제 생성
            val user = helper.createActiveStudent("25-610", "사용자K")
            val task1 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "가온A", user)
            val task2 = helper.createTranslationTask(TranslationTask.TaskType.GAONNURI_POST, "가온B", user)
            helper.createTranslationTask(TranslationTask.TaskType.EXTERNAL_POST, "외부과제", user)

            // assignedAt 설정 (시간 차이)
            val now = java.time.Instant.now()
            helper.setTaskAssignedAt(task1.id, now.minusSeconds(60 * 60))
            helper.setTaskAssignedAt(task2.id, now.minusSeconds(30 * 60))

            // when & then - 가온누리 과제만 + 배정 시간 내림차순
            mockMvc.get("/tasks?page=0&size=10&taskType=GAONNURI_POST&sortField=assignedAt&sortDirection=DESC") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.content[0].taskDescription") { value("가온B") }
                jsonPath("$.content[1].taskDescription") { value("가온A") }
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
        helper.setTaskCompletedAt(taskId, java.time.Instant.now(), characterCount)
    }

    @Nested
    @DisplayName("PATCH /tasks/{taskId}/assignee - 담당자 변경")
    inner class ChangeAssignee {

        @Test
        fun `미완료 과제의 담당자를 변경하면 200과 새 담당자 정보를 반환한다`() {
            // given - 과제와 새 담당자 생성
            val originalAssignee = helper.createActiveStudent("25-400", "원래담당자")
            val newAssignee = helper.createActiveStudent("25-401", "새담당자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "담당자 변경 테스트 과제",
                originalAssignee
            )

            val request = mapOf(
                "newAssigneeId" to newAssignee.id.toString(),
            )

            // when & then
            mockMvc.patch("/tasks/${task.id}/assignee") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.taskId") { value(task.id.toString()) }
                jsonPath("$.newAssigneeId") { value(newAssignee.id.toString()) }
                jsonPath("$.newAssigneeStudentNumber") { value("25-401") }
                jsonPath("$.newAssigneeName") { value("새담당자") }
            }
        }

        @Test
        fun `완료된 과제의 담당자를 변경하면 400을 반환한다`() {
            // given - 완료된 과제 생성
            val originalAssignee = helper.createActiveStudent("25-402", "완료과제담당자")
            val newAssignee = helper.createActiveStudent("25-403", "새담당자")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "완료된 과제",
                originalAssignee
            )
            completeTask(task.id, 1000)

            val request = mapOf(
                "newAssigneeId" to newAssignee.id.toString(),
            )

            // when & then
            mockMvc.patch("/tasks/${task.id}/assignee") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `존재하지 않는 과제의 담당자를 변경하면 400을 반환한다`() {
            // given
            val newAssignee = helper.createActiveStudent("25-404", "새담당자")
            val nonExistentTaskId = UUID.randomUUID()

            val request = mapOf(
                "newAssigneeId" to newAssignee.id.toString(),
            )

            // when & then
            mockMvc.patch("/tasks/$nonExistentTaskId/assignee") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
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

            val request = mapOf(
                "newAssigneeId" to nonExistentUserId.toString(),
            )

            // when & then
            mockMvc.patch("/tasks/${task.id}/assignee") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `휴식 상태인 사용자로 담당자를 변경하면 400을 반환한다`() {
            // given
            val originalAssignee = helper.createActiveStudent("25-406", "원래담당자")
            helper.createActiveStudent("25-407", "다른사용자") // 마지막 활성 사용자 제약 회피
            val restingUser = helper.createActiveStudent("25-408", "휴식중인사용자")
            helper.setUserResting(restingUser.id, true)
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "휴식 사용자 테스트",
                originalAssignee
            )

            val request = mapOf(
                "newAssigneeId" to restingUser.id.toString(),
            )

            // when & then
            mockMvc.patch("/tasks/${task.id}/assignee") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
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

            val request = mapOf(
                "newAssigneeId" to newAssignee.id.toString(),
            )

            // when
            mockMvc.patch("/tasks/${task.id}/assignee") {
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
    @DisplayName("DELETE /tasks/{taskId} - 과제 삭제")
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

            // when & then
            mockMvc.delete("/tasks/${task.id}") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isNoContent() }
            }
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
            mockMvc.delete("/tasks/${task.id}") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isNoContent() }
            }

            // then - 목록에서 해당 과제가 없어야 함
            val result = mockMvc.get("/tasks?page=0&size=100") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andReturn()

            assertThat(result.response.status).isEqualTo(200)
            val responseJson = objectMapper.readTree(result.response.contentAsString)
            val taskIds = responseJson.path("content").map { it.path("id").asText() }
            assertThat(taskIds).doesNotContain(task.id.toString())
        }

        @Test
        fun `존재하지 않는 과제를 삭제하면 400을 반환한다`() {
            // given
            val nonExistentTaskId = UUID.randomUUID()

            // when & then
            mockMvc.delete("/tasks/$nonExistentTaskId") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `완료된 과제도 삭제할 수 있다`() {
            // given - 완료된 과제 생성
            val assignee = helper.createActiveStudent("25-502", "완료과제삭제테스트")
            val task = helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "완료된 삭제 과제",
                assignee
            )
            completeTask(task.id, 1000)

            // when & then
            mockMvc.delete("/tasks/${task.id}") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isNoContent() }
            }
        }
    }
}

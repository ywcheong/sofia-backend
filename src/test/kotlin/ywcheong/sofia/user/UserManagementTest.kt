package ywcheong.sofia.user

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
@DisplayName("사용자 관리")
class UserManagementTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)
        // 관리자를 휴식 상태로 설정 (배정/활성 사용자 대상에서 제외)
        helper.setUserResting(adminInfo.userId, true)
    }

    @Nested
    @DisplayName("GET /users - 사용자 목록 조회")
    inner class FindAllUsers {

        @Test
        fun `페이지네이션으로 사용자 목록을 조회하면 200을 반환한다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-001", "사용자1")
            helper.createActiveStudent("25-002", "사용자2")
            helper.createActiveStudent("25-003", "사용자3")

            // when & then
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.totalElements") { isNumber() }
                jsonPath("$.totalPages") { isNumber() }
                jsonPath("$.size") { value(10) }
                jsonPath("$.number") { value(0) }
            }
        }

        @Test
        fun `첫 번째 페이지에는 요청한 개수만큼의 사용자가 반환된다`() {
            // given - 여러 사용자 생성
            helper.createActiveStudent("25-010", "사용자A")
            helper.createActiveStudent("25-011", "사용자B")

            // when & then
            mockMvc.get("/users?page=0&size=1") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.totalElements") { value(3) } // admin + 2 users
            }
        }

        @Test
        fun `사용자 응답에 필요한 필드가 모두 포함된다`() {
            // given
            val user = helper.createActiveStudent("25-020", "테스트사용자")

            // when & then
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].id") { isString() }
                jsonPath("$.content[0].studentNumber") { isString() }
                jsonPath("$.content[0].studentName") { isString() }
                jsonPath("$.content[0].role") { isString() }
                jsonPath("$.content[0].rest") { isBoolean() }
                jsonPath("$.content[0].warningCount") { isNumber() }
                jsonPath("$.content[0].adjustedCharCount") { isNumber() }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/rest - 개인 휴식 설정")
    inner class SetRestStatus {

        @Test
        fun `휴식 상태로 변경하면 200을 반환한다`() {
            // given - 활성 사용자 2명 이상 생성 (마지막 사용자 제약 회피)
            helper.createActiveStudent("25-020", "사용자나")
            val user = helper.createActiveStudent("25-021", "사용자다")

            val request = mapOf(
                "rest" to true,
            )

            // when & then (관리자 권한 필요)
            mockMvc.post("/users/${user.id}/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.rest") { value(true) }
            }
        }

        @Test
        fun `활성 상태로 변경하면 200을 반환한다`() {
            // given - 휴식 상태 사용자 생성 (마지막 활성 사용자 제약 회피 위해 추가 사용자 생성)
            helper.createActiveStudent("25-021", "다른사용자")
            val user = helper.createActiveStudent("25-022", "휴식자")
            helper.setUserResting(user.id, true)

            val request = mapOf(
                "rest" to false,
            )

            // when & then
            mockMvc.post("/users/${user.id}/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.rest") { value(false) }
            }
        }

        @Test
        fun `존재하지 않는 사용자면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf(
                "isResting" to true,
            )

            // when & then
            mockMvc.post("/users/$nonExistentId/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        @Transactional
        fun `마지막 활성 사용자가 휴식 상태로 변경하면 400을 반환한다`() {
            // given - 단 하나의 활성 사용자
            val onlyActiveUser = helper.createActiveStudent("25-030", "유일한활성자")

            val request = mapOf(
                "isResting" to true,
            )

            // when & then
            mockMvc.post("/users/${onlyActiveUser.id}/rest") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/adjust-char-count - 보정 자수 조정")
    inner class AdjustCharCount {

        @Test
        fun `양수로 보정 자수를 부여하면 200을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-040", "사용자")

            val request = mapOf(
                "amount" to 500,
            )

            // when & then (관리자 권한 필요)
            mockMvc.post("/users/${user.id}/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.amount") { value(500) }
                jsonPath("$.adjustedCharCount") { value(500) }
            }
        }

        @Test
        fun `음수로 보정 자수를 차감하면 200을 반환한다`() {
            // given - 보정 자수가 있는 사용자 생성
            val user = helper.createActiveStudent("25-041", "사용자")
            adjustCharCount(user.id, 100) // 먼저 100 부여

            val request = mapOf(
                "amount" to -300,
            )

            // when & then
            mockMvc.post("/users/${user.id}/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.amount") { value(-300) }
                jsonPath("$.adjustedCharCount") { value(-200) }
            }
        }

        @Test
        fun `존재하지 않는 사용자면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf(
                "amount" to 500,
            )

            // when & then
            mockMvc.post("/users/$nonExistentId/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `보정 자수가 0이면 400을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-042", "사용자")

            val request = mapOf(
                "amount" to 0,
            )

            // when & then
            mockMvc.post("/users/${user.id}/adjust-char-count") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/promote - 관리자 승급")
    inner class PromoteToAdmin {

        @Test
        fun `일반 학생을 관리자로 승급하면 200을 반환한다`() {
            // given - 일반 학생 생성
            val student = helper.createActiveStudent("25-050", "학생")

            // when & then
            mockMvc.post("/users/${student.id}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(student.id.toString()) }
                jsonPath("$.role") { value("ADMIN") }
            }
        }

        @Test
        fun `관리자 승급 시 승급 알림 이메일이 발송된다`() {
            // given - 이메일 구독 중인 일반 학생 생성
            val student = helper.createActiveStudent("25-051", "학생")
            val mailSender = helper.getMailSender()

            // when
            mockMvc.post("/users/${student.id}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // then - 관리자 승급 이메일이 발송되었는지 확인
            val emails = mailSender.getMessagesBySubject("관리자 권한이 부여되었습니다")
            assert(emails.isNotEmpty()) { "관리자 승급 이메일이 발송되어야 합니다" }

            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assert(emailInfo.content.contains("학생")) { "수신자 이름이 본문에 포함되어야 합니다" }
        }

        @Test
        fun `이미 관리자인 사용자를 승급하면 400을 반환한다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // when & then
            mockMvc.post("/users/${otherAdmin.userId}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `존재하지 않는 사용자를 승급하면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()

            // when & then
            mockMvc.post("/users/$nonExistentId/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /users/{userId}/demote - 관리자 강등")
    inner class DemoteFromAdmin {

        @Test
        fun `관리자를 일반 학생으로 강등하면 200을 반환한다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // when & then
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(otherAdmin.userId.toString()) }
                jsonPath("$.role") { value("STUDENT") }
            }
        }

        @Test
        fun `관리자 강등 시 강등 알림 이메일이 발송된다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin3", "관리자3")
            val mailSender = helper.getMailSender()

            // when
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // then - 관리자 강등 이메일이 발송되었는지 확인
            val emails = mailSender.getMessagesBySubject("관리자 권한이 해제되었습니다")
            assert(emails.isNotEmpty()) { "관리자 강등 이메일이 발송되어야 합니다" }

            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assert(emailInfo.content.contains("관리자3")) { "수신자 이름이 본문에 포함되어야 합니다" }
        }

        @Test
        fun `자기 자신을 강등하면 400을 반환한다`() {
            // when & then - adminInfo 사용자가 자기 자신을 강등 시도
            mockMvc.post("/users/${adminInfo.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `일반 학생을 강등하면 400을 반환한다`() {
            // given - 일반 학생 생성
            val student = helper.createActiveStudent("25-060", "학생")

            // when & then
            mockMvc.post("/users/${student.id}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `마지막 관리자를 강등하면 400을 반환한다`() {
            // given - 관리자가 한 명만 있는 상황 (이미 adminInfo만 존재)
            // adminInfo 외에 다른 관리자가 없으므로 마지막 관리자

            // when & then - 다른 관리자가 없는 상태에서 강등 불가
            // 먼저 다른 관리자를 만들고 강등하면 adminInfo가 마지막 관리자가 됨
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // otherAdmin을 강등 (성공해야 함)
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // 이제 adminInfo가 마지막 관리자 - 다시 강등하려고 하면 실패
            // 이미 STUDENT가 된 otherAdmin을 다시 관리자로 승급
            mockMvc.post("/users/${otherAdmin.userId}/promote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // adminInfo(현재 로그인한 관리자)가 otherAdmin을 강등
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            // 이제 adminInfo가 마지막 관리자
            // adminInfo가 자신이 아닌 다른 사용자(이미 학생이 된 otherAdmin)를 강등하려고 하면
            // 그 사용자는 이미 학생이므로 "관리자 권한이 없는 사용자입니다" 에러
            mockMvc.post("/users/${otherAdmin.userId}/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `존재하지 않는 사용자를 강등하면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()

            // when & then
            mockMvc.post("/users/$nonExistentId/demote") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    // 헬퍼 메서드: 보정 자수 조정 (API 사용)
    private fun adjustCharCount(userId: UUID, amount: Int) {
        val request = mapOf(
            "amount" to amount,
        )
        mockMvc.post("/users/$userId/adjust-char-count") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
        }.andExpect {
            status { isOk() }
        }
    }
}

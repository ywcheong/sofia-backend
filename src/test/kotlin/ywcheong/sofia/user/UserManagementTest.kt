package ywcheong.sofia.user

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.RequestHelper
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
    private lateinit var requestHelper: RequestHelper
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupTranslationScenario()
        requestHelper = RequestHelper(mockMvc, objectMapper, helper)
    }

    @Nested
    @DisplayName("휴식 상태 설정")
    inner class SetRestStatusTests {

        @Test
        fun `휴식 상태로 변경하면 200을 반환한다`() {
            // given - 활성 사용자 2명 이상 생성 (마지막 사용자 제약 회피)
            helper.createActiveStudent("25-020", "사용자나")
            val user = helper.createActiveStudent("25-021", "사용자다")

            val request = mapOf("rest" to true)

            // when & then
            val result = requestHelper.post("/users/${user.id}/rest", request, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `활성 상태로 변경하면 200을 반환한다`() {
            // given - 휴식 상태 사용자 생성
            helper.createActiveStudent("25-021", "다른사용자")
            val user = helper.createActiveStudent("25-022", "휴식자")
            helper.setUserResting(user.id, true)

            val request = mapOf("rest" to false)

            // when & then
            val result = requestHelper.post("/users/${user.id}/rest", request, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `존재하지 않는 사용자면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf("rest" to true)

            // when & then
            val result = requestHelper.post("/users/$nonExistentId/rest", request, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        @Transactional
        fun `마지막 활성 사용자가 휴식 상태로 변경하면 400을 반환한다`() {
            // given - 단 하나의 활성 사용자
            val onlyActiveUser = helper.createActiveStudent("25-030", "유일한활성자")

            val request = mapOf("rest" to true)

            // when & then
            val result = requestHelper.post("/users/${onlyActiveUser.id}/rest", request, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }
    }

    @Nested
    @DisplayName("보정 자수 조정")
    inner class AdjustCharCountTests {

        @Test
        fun `양수로 보정 자수를 부여하면 200을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-040", "사용자")

            val request = mapOf("amount" to 500)

            // when & then
            val result = requestHelper.post("/users/${user.id}/adjust-char-count", request, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `음수로 보정 자수를 차감하면 200을 반환한다`() {
            // given - 보정 자수가 있는 사용자 생성
            val user = helper.createActiveStudent("25-041", "사용자")
            adjustCharCount(user.id, 100) // 먼저 100 부여

            val request = mapOf("amount" to -300)

            // when & then
            val result = requestHelper.post("/users/${user.id}/adjust-char-count", request, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `존재하지 않는 사용자면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf("amount" to 500)

            // when & then
            val result = requestHelper.post("/users/$nonExistentId/adjust-char-count", request, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `보정 자수가 0이면 400을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-042", "사용자")

            val request = mapOf("amount" to 0)

            // when & then
            val result = requestHelper.post("/users/${user.id}/adjust-char-count", request, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }
    }

    @Nested
    @DisplayName("경고 수 조정")
    inner class AdjustWarningCountTests {

        @Test
        fun `양수로 경고를 부여하면 200을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-043", "사용자")

            val request = mapOf("amount" to 1)

            // when & then
            val result = requestHelper.post("/users/${user.id}/adjust-warning-count", request, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `음수로 경고를 차감하면 200을 반환한다`() {
            // given - 경고가 있는 사용자 생성
            val user = helper.createActiveStudent("25-044", "사용자")
            adjustWarningCount(user.id, 2) // 먼저 2 부여

            val request = mapOf("amount" to -1)

            // when & then
            val result = requestHelper.post("/users/${user.id}/adjust-warning-count", request, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `경고 총 갯수가 음수가 된다면 400을 반환한다`() {
            // given - 경고가 있는 사용자 생성
            val user = helper.createActiveStudent("25-044b", "사용자")
            adjustWarningCount(user.id, 1)

            val request = mapOf("amount" to -2)

            // when & then
            val result = requestHelper.post("/users/${user.id}/adjust-warning-count", request, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `존재하지 않는 사용자면 400을 반환한다 - 경고`() {
            // given
            val nonExistentId = UUID.randomUUID()
            val request = mapOf("amount" to 1)

            // when & then
            val result = requestHelper.post("/users/$nonExistentId/adjust-warning-count", request, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `경고 수가 0이면 400을 반환한다`() {
            // given - 활성 사용자 생성
            val user = helper.createActiveStudent("25-045", "사용자")

            val request = mapOf("amount" to 0)

            // when & then
            val result = requestHelper.post("/users/${user.id}/adjust-warning-count", request, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }
    }

    @Nested
    @DisplayName("관리자 승급")
    inner class PromoteToAdminTests {

        @Test
        fun `일반 학생을 관리자로 승급하면 200을 반환한다`() {
            // given - 일반 학생 생성
            val student = helper.createActiveStudent("25-050", "학생")

            // when & then
            val result = requestHelper.post("/users/${student.id}/promote", null, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `관리자 승급 시 승급 알림 이메일이 발송된다`() {
            // given - 이메일 구독 중인 일반 학생 생성
            val student = helper.createActiveStudent("25-051", "학생")
            val mailSender = helper.getMailSender()

            // when
            val result = requestHelper.post("/users/${student.id}/promote", null, adminInfo.secretToken)
            requestHelper.assertOk(result)

            // then - 관리자 승급 이메일이 발송되었는지 확인
            val emails = mailSender.getMessagesBySubject("관리자 권한이 부여되었습니다")
            check(emails.isNotEmpty()) { "관리자 승급 이메일이 발송되어야 합니다" }

            val emailInfo = mailSender.extractEmailInfo(emails.first())
            check(emailInfo.content.contains("학생")) { "수신자 이름이 본문에 포함되어야 합니다" }
        }

        @Test
        fun `이미 관리자인 사용자를 승급하면 400을 반환한다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // when & then
            val result = requestHelper.post("/users/${otherAdmin.userId}/promote", null, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `존재하지 않는 사용자를 승급하면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()

            // when & then
            val result = requestHelper.post("/users/$nonExistentId/promote", null, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }
    }

    @Nested
    @DisplayName("관리자 강등")
    inner class DemoteFromAdminTests {

        @Test
        fun `관리자를 일반 학생으로 강등하면 200을 반환한다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // when & then
            val result = requestHelper.post("/users/${otherAdmin.userId}/demote", null, adminInfo.secretToken)
            requestHelper.assertOk(result)
        }

        @Test
        fun `관리자 강등 시 강등 알림 이메일이 발송된다`() {
            // given - 다른 관리자 생성
            val otherAdmin = helper.createAdminAndGetToken("admin3", "관리자3")
            val mailSender = helper.getMailSender()

            // when
            val result = requestHelper.post("/users/${otherAdmin.userId}/demote", null, adminInfo.secretToken)
            requestHelper.assertOk(result)

            // then - 관리자 강등 이메일이 발송되었는지 확인
            val emails = mailSender.getMessagesBySubject("관리자 권한이 해제되었습니다")
            check(emails.isNotEmpty()) { "관리자 강등 이메일이 발송되어야 합니다" }

            val emailInfo = mailSender.extractEmailInfo(emails.first())
            check(emailInfo.content.contains("관리자3")) { "수신자 이름이 본문에 포함되어야 합니다" }
        }

        @Test
        fun `자기 자신을 강등하면 400을 반환한다`() {
            // when & then - adminInfo 사용자가 자기 자신을 강등 시도
            val result = requestHelper.post("/users/${adminInfo.userId}/demote", null, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `일반 학생을 강등하면 400을 반환한다`() {
            // given - 일반 학생 생성
            val student = helper.createActiveStudent("25-060", "학생")

            // when & then
            val result = requestHelper.post("/users/${student.id}/demote", null, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `마지막 관리자를 강등하면 400을 반환한다`() {
            // given - 관리자가 한 명만 있는 상황 시뮬레이션
            val otherAdmin = helper.createAdminAndGetToken("admin2", "관리자2")

            // otherAdmin을 강등 (성공)
            var result = requestHelper.post("/users/${otherAdmin.userId}/demote", null, adminInfo.secretToken)
            requestHelper.assertOk(result)

            // 다시 승급
            result = requestHelper.post("/users/${otherAdmin.userId}/promote", null, adminInfo.secretToken)
            requestHelper.assertOk(result)

            // 다시 강등
            result = requestHelper.post("/users/${otherAdmin.userId}/demote", null, adminInfo.secretToken)
            requestHelper.assertOk(result)

            // 이제 adminInfo가 마지막 관리자, 이미 학생이 된 otherAdmin을 강등하려고 하면
            result = requestHelper.post("/users/${otherAdmin.userId}/demote", null, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }

        @Test
        fun `존재하지 않는 사용자를 강등하면 400을 반환한다`() {
            // given
            val nonExistentId = UUID.randomUUID()

            // when & then
            val result = requestHelper.post("/users/$nonExistentId/demote", null, adminInfo.secretToken)
            requestHelper.assertBadRequest(result)
        }
    }

    // 헬퍼 메서드: 보정 자수 조정 (API 사용)
    private fun adjustCharCount(userId: UUID, amount: Int) {
        val request = mapOf("amount" to amount)
        val result = requestHelper.post("/users/$userId/adjust-char-count", request, adminInfo.secretToken)
        requestHelper.assertOk(result)
    }

    // 헬퍼 메서드: 경고 수 조정 (API 사용)
    private fun adjustWarningCount(userId: UUID, amount: Int) {
        val request = mapOf("amount" to amount)
        val result = requestHelper.post("/users/$userId/adjust-warning-count", request, adminInfo.secretToken)
        requestHelper.assertOk(result)
    }
}

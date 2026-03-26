package ywcheong.sofia.user.registration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.kakao.FakeKakaoMessageSimulator
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.SofiaUserRepository

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("회원가입")
class SofiaUserRegistrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val sofiaUserRepository: SofiaUserRepository,
    private val helper: TestScenarioHelper,
) {
    private lateinit var simulator: FakeKakaoMessageSimulator
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.RECRUITMENT)
        simulator = FakeKakaoMessageSimulator(
            mockMvc = mockMvc,
            objectMapper = objectMapper,
            testScenarioHelper = helper
        )
    }

    @Nested
    @DisplayName("GET /user/registrations - 신청 목록 조회")
    inner class FindAllRegistrations {

        @Test
        fun `페이지네이션으로 신청 목록을 조회하면 200을 반환한다`() {
            // given - 여러 신청 생성
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = "test-key-1",
                action = "registration_apply",
                actionData = mapOf("studentNumber" to "25-001", "studentName" to "김갑동")
            )
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = "test-key-2",
                action = "registration_apply",
                actionData = mapOf("studentNumber" to "25-002", "studentName" to "박을순")
            )

            // when & then
            mockMvc.get("/user/registrations?page=0&size=10") {
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
        fun `첫 번째 페이지에는 요청한 개수만큼의 신청이 반환된다`() {
            // given - 여러 신청 생성
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = "test-key-10",
                action = "registration_apply",
                actionData = mapOf("studentNumber" to "25-010", "studentName" to "신청자A")
            )
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = "test-key-11",
                action = "registration_apply",
                actionData = mapOf("studentNumber" to "25-011", "studentName" to "신청자B")
            )

            // when & then
            mockMvc.get("/user/registrations?page=0&size=1") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.totalElements") { value(2) }
            }
        }

        @Test
        fun `신청 응답에 필요한 필드가 모두 포함된다`() {
            // given
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = "test-key-20",
                action = "registration_apply",
                actionData = mapOf("studentNumber" to "25-020", "studentName" to "테스트신청자")
            )

            // when & then
            mockMvc.get("/user/registrations?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.content[0].id") { isString() }
                jsonPath("$.content[0].studentNumber") { isString() }
                jsonPath("$.content[0].studentName") { isString() }
            }
        }
    }

    @Nested
    @DisplayName("registration_apply 액션 - 회원가입 신청")
    inner class ApplyRegistration {
        @Test
        fun `유효한 요청이면 성공 메시지를 반환한다`() {
            // given & when
            val result = simulator.sendMessage(
                fromUser = null,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-001",
                    "studentName" to "홍길동"
                )
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("번역버디 권한을 신청했습니다")) { "성공 메시지가 포함되어야 합니다" }
            assert(response.contains("25-001")) { "학번이 포함되어야 합니다" }
            assert(response.contains("홍길동")) { "이름이 포함되어야 합니다" }
        }

        @Test
        fun `학번 형식이 올바르지 않으면 에러 메시지를 반환한다`() {
            // given & when
            val result = simulator.sendMessage(
                fromUser = null,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "invalid",
                    "studentName" to "홍길동"
                )
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("학번은 00-000 형식이어야 합니다")) { "학번 형식 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `이름이 형식에 맞지 않으면 에러 메시지를 반환한다`() {
            // given & when
            val result = simulator.sendMessage(
                fromUser = null,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-001",
                    "studentName" to "1"
                )
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("이름은")) { "이름 형식 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `이미 가입된 학번으로 신청하면 에러 메시지를 반환한다`() {
            // given - 활성 유저 생성
            sofiaUserRepository.save(
                SofiaUser(
                    studentNumber = "25-001",
                    studentName = "홍길동",
                    plusfriendUserKey = "existing-user-key"
                )
            )

            // when
            val result = simulator.sendMessage(
                fromUser = null,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-001",
                    "studentName" to "김철수"
                )
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("이미 가입된 학번입니다")) { "중복 학번 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `이미 신청된 학번으로 다시 신청하면 에러 메시지를 반환한다`() {
            // given - 먼저 신청 생성
            simulator.sendMessage(
                fromUser = null,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-001",
                    "studentName" to "홍길동"
                )
            )

            // when
            val result = simulator.sendMessage(
                fromUser = null,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-001",
                    "studentName" to "김철수"
                )
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("이미 신청된 학번입니다")) { "중복 신청 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `이미 가입된 사용자가 신청하면 에러 메시지를 반환한다`() {
            // given - 활성 유저 생성
            val user = helper.createActiveStudent("25-001", "홍길동")

            // when
            val result = simulator.sendMessage(
                fromUser = user,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-002",
                    "studentName" to "김철수"
                )
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("이미 가입을 요청했거나")) { "이미 가입된 사용자 에러 메시지가 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("registration_cancel 액션 - 회원가입 취소")
    inner class CancelRegistration {

        @Test
        fun `확인 메시지와 함께 취소하면 성공 메시지를 반환한다`() {
            // given - 신청 생성 (동일한 plusfriendUserKey 사용)
            val plusfriendUserKey = "test-user-key-cancel"
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = plusfriendUserKey,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-001",
                    "studentName" to "홍길동"
                )
            )

            // when - 취소 확인
            val result = simulator.sendMessageFromAnonymous(
                plusfriendUserKey = plusfriendUserKey,
                action = "registration_cancel",
                actionData = mapOf("sure" to "네, 취소합니다.")
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("요청이 삭제되었습니다")) { "취소 성공 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `확인 메시지 없이 취소하면 취소하지 않는다는 메시지를 반환한다`() {
            // given & when
            val result = simulator.sendMessage(
                fromUser = null,
                action = "registration_cancel",
                actionData = mapOf("sure" to "아니오")
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("가입 요청을 철회하지 않겠습니다")) { "취소하지 않는다는 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `가입 요청이 없으면 에러 메시지를 반환한다`() {
            // given & when - 신청 없이 취소 시도
            val result = simulator.sendMessage(
                fromUser = null,
                action = "registration_cancel",
                actionData = mapOf("sure" to "네, 취소합니다.")
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("가입 요청을 한 적이 없습니다")) { "가입 요청 없음 에러 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `승인된 사용자가 취소하면 에러 메시지를 반환한다`() {
            // given - 활성 유저 생성
            val user = helper.createActiveStudent("25-001", "홍길동")

            // when
            val result = simulator.sendMessage(
                fromUser = user,
                action = "registration_cancel",
                actionData = mapOf("sure" to "네, 취소합니다.")
            )

            // then
            val response = result.response.contentAsString
            assert(response.contains("승인된 번역버디는 가입 취소가 불가합니다")) { "승인된 사용자 에러 메시지가 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("POST /user/registrations/{id}/acceptance - 가입 승인")
    inner class AcceptRegistration {

        @Test
        fun `가입 승인 시 승인 이메일이 발송된다`() {
            // given - 회원가입 신청 생성
            val plusfriendUserKey = "test-accept-user-key"
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = plusfriendUserKey,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-001",
                    "studentName" to "홍길동"
                )
            )

            val registration = helper.findRegistrationByPlusfriendUserKey(plusfriendUserKey)
            checkNotNull(registration) { "신청이 생성되어야 합니다" }

            // when - 가입 승인
            mockMvc.perform(
                post("/user/registrations/${registration.id}/acceptance")
                    .header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            ).andExpect(status().isOk)

            // then - 이메일 발송 검증
            val mailSender = helper.getMailSender()
            val emails = mailSender.getMessagesBySubject("가입 신청이 승인되었습니다")

            assert(emails.size == 1) { "승인 이메일이 1건 발송되어야 합니다" }
            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assert(emailInfo.content.contains("홍길동")) { "수신자 이름이 본문에 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("POST /user/registrations/{id}/rejection - 가입 거절")
    inner class DenyRegistration {

        @Test
        fun `가입 거절 시 거절 이메일이 발송된다`() {
            // given - 회원가입 신청 생성
            val plusfriendUserKey = "test-deny-user-key"
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = plusfriendUserKey,
                action = "registration_apply",
                actionData = mapOf(
                    "studentNumber" to "25-002",
                    "studentName" to "김철수"
                )
            )

            val registration = helper.findRegistrationByPlusfriendUserKey(plusfriendUserKey)
            checkNotNull(registration) { "신청이 생성되어야 합니다" }

            // when - 가입 거절
            mockMvc.perform(
                post("/user/registrations/${registration.id}/rejection")
                    .header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            ).andExpect(status().isOk)

            // then - 이메일 발송 검증
            val mailSender = helper.getMailSender()
            val emails = mailSender.getMessagesBySubject("가입 신청이 거절되었습니다")

            assert(emails.size == 1) { "거절 이메일이 1건 발송되어야 합니다" }
            val emailInfo = mailSender.extractEmailInfo(emails.first())
            assert(emailInfo.content.contains("김철수")) { "수신자 이름이 본문에 포함되어야 합니다" }
            assert(emailInfo.recipients.any { it.contains("25-002@ksa.hs.kr") }) { "수신자 이메일이 학번으로 생성되어야 합니다" }
        }
    }
}

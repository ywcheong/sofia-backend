package ywcheong.sofia.email

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.email.templates.TaskAssignmentEmailTemplate
import ywcheong.sofia.email.user.SofiaUserEmailRepository
import ywcheong.sofia.phase.SystemPhase

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("이메일 수신 설정")
class EmailSubscriptionTest(
    private val mockMvc: MockMvc,
    private val helper: TestScenarioHelper,
    private val userEmailRepository: SofiaUserEmailRepository,
    private val emailSendService: EmailSendService,
) {

    @BeforeEach
    fun setUp() {
        helper.setupScenario(SystemPhase.RECRUITMENT)
    }

    @Nested
    @DisplayName("수신 상태 조회")
    inner class GetStatus {

        @Test
        fun `토큰으로 수신 상태를 조회할 수 있다`() {
            // given: 사용자 생성
            val user = helper.createActiveStudent("2024001", "홍길동")
            val userEmail = userEmailRepository.findById(user.id).orElseThrow()

            // when & then: 토큰으로 조회
            mockMvc.perform(get("/api/email/subscription/${userEmail.unsubscribeToken}/status"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.email").value("2024001@ksa.hs.kr"))
                .andExpect(jsonPath("$.isSubscribed").value(true))
        }

        @Test
        fun `잘못된 토큰으로 조회하면 에러가 발생한다`() {
            // when & then: 존재하지 않는 토큰
            mockMvc.perform(get("/api/email/subscription/00000000-0000-0000-0000-000000000000/status"))
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("수신 거부")
    inner class Unsubscribe {

        @Test
        fun `토큰으로 수신 거부할 수 있다`() {
            // given: 사용자 생성
            val user = helper.createActiveStudent("2024002", "김철수")
            val userEmail = userEmailRepository.findById(user.id).orElseThrow()

            // when: 수신 거부
            mockMvc.perform(put("/api/email/subscription/${userEmail.unsubscribeToken}/unsubscribe"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isSubscribed").value(false))

            // then: 상태 조회로 확인
            mockMvc.perform(get("/api/email/subscription/${userEmail.unsubscribeToken}/status"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isSubscribed").value(false))
        }
    }

    @Nested
    @DisplayName("수신 허용")
    inner class Subscribe {

        @Test
        fun `수신 거부 후 다시 수신 허용할 수 있다`() {
            // given: 사용자 생성 후 수신 거부
            val user = helper.createActiveStudent("2024003", "이영희")
            val userEmail = userEmailRepository.findById(user.id).orElseThrow()

            mockMvc.perform(put("/api/email/subscription/${userEmail.unsubscribeToken}/unsubscribe"))
                .andExpect(status().isOk)

            // when: 수신 허용
            mockMvc.perform(put("/api/email/subscription/${userEmail.unsubscribeToken}/subscribe"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isSubscribed").value(true))

            // then: 상태 확인
            mockMvc.perform(get("/api/email/subscription/${userEmail.unsubscribeToken}/status"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isSubscribed").value(true))
        }
    }

    @Nested
    @DisplayName("수신 거부 시 이메일 발송 생략")
    inner class UnsubscribedEmailSend {

        @Test
        fun `수신 거부한 사용자에게는 이메일이 발송되지 않는다`() {
            // given: 사용자 생성 후 수신 거부
            val user = helper.createActiveStudent("2024004", "박수신거부")
            val userEmail = userEmailRepository.findById(user.id).orElseThrow()
            userEmail.unsubscribe()
            userEmailRepository.save(userEmail)

            val template = TaskAssignmentEmailTemplate(
                recipientEmail = userEmail.email,
                recipientName = user.studentName,
                unsubscribeToken = userEmail.unsubscribeToken.toString(),
                taskTitle = "테스트 과제",
                taskDeadline = "2024-12-31",
                taskLink = "https://example.com/task/1"
            )

            // when: 이메일 발송 시도
            val result = emailSendService.sendEmail(template)

            // then: 발송 실패 처리됨
            assert(!result.success)
            assert(result.errorMessage == "Recipient has unsubscribed from emails")

            // and: 실제로 메일이 발송되지 않음
            val mailSender = helper.getMailSender()
            val sentToUser = mailSender.getMessagesTo(userEmail.email)
            assert(sentToUser.isEmpty()) { "수신 거부한 사용자에게 메일이 발송되었습니다" }
        }

        @Test
        fun `수신 허용한 사용자에게는 이메일이 정상 발송된다`() {
            // given: 수신 허용 상태의 사용자
            val user = helper.createActiveStudent("2024005", "김수신허용")
            val userEmail = userEmailRepository.findById(user.id).orElseThrow()

            val template = TaskAssignmentEmailTemplate(
                recipientEmail = userEmail.email,
                recipientName = user.studentName,
                unsubscribeToken = userEmail.unsubscribeToken.toString(),
                taskTitle = "테스트 과제",
                taskDeadline = "2024-12-31",
                taskLink = "https://example.com/task/1"
            )

            // when: 이메일 발송
            val result = emailSendService.sendEmail(template)

            // then: 발송 성공
            assert(result.success)

            // and: 실제 메일 발송됨
            val mailSender = helper.getMailSender()
            val sentToUser = mailSender.getMessagesTo(userEmail.email)
            assert(sentToUser.isNotEmpty()) { "수신 허용한 사용자에게 메일이 발송되지 않았습니다" }
        }
    }
}

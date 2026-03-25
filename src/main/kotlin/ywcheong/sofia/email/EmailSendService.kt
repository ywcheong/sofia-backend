package ywcheong.sofia.email

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import ywcheong.sofia.email.user.SofiaUserEmailRepository
import java.util.UUID

interface EmailSendService {
    fun sendEmail(template: EmailTemplate): EmailSendResult

    data class EmailSendResult(
        val success: Boolean,
        val messageId: String?,
        val errorMessage: String?,
    )

    @Service
    class DefaultEmailSendService(
        private val emailContentGenerateService: EmailContentGenerateService,
        private val emailProperties: EmailProperties,
        private val userEmailRepository: SofiaUserEmailRepository,
        private val mailSender: JavaMailSender,
    ) : EmailSendService {
        private val logger = KotlinLogging.logger {}

        override fun sendEmail(template: EmailTemplate): EmailSendResult {
            val userEmail = userEmailRepository.findByUnsubscribeToken(UUID.fromString(template.unsubscribeToken))

            if (userEmail != null && userEmail.isUnsubscribed) {
                logger.info { "이메일 발송 생략 (수신거부): to=${template.recipientEmail}, templateId=${template.templateId}" }
                return EmailSendResult(
                    success = false,
                    messageId = null,
                    errorMessage = "Recipient has unsubscribed from emails",
                )
            }

            if (!emailProperties.enabled) {
                logger.info { "이메일 발송 생략 (비활성화): to=${template.recipientEmail}, templateId=${template.templateId}" }
                return EmailSendResult(
                    success = true,
                    messageId = null,
                    errorMessage = null,
                )
            }

            val content = emailContentGenerateService.generateEmailContent(template)

            return try {
                val mimeMessage = mailSender.createMimeMessage()
                val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

                helper.setFrom(emailProperties.fromEmail, emailProperties.fromName)
                helper.setTo(template.recipientEmail)
                helper.setSubject("[SOFIA] ${template.subject}")
                helper.setText(content, true)

                mailSender.send(mimeMessage)

                logger.info { "이메일 발송 완료: to=${template.recipientEmail}, templateId=${template.templateId}" }

                EmailSendResult(
                    success = true,
                    messageId = UUID.randomUUID().toString(),
                    errorMessage = null,
                )
            } catch (e: Exception) {
                logger.error(e) { "이메일 발송 실패: to=${template.recipientEmail}, templateId=${template.templateId}" }
                EmailSendResult(
                    success = false,
                    messageId = null,
                    errorMessage = e.message,
                )
            }
        }
    }
}

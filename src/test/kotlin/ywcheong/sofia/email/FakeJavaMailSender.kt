package ywcheong.sofia.email

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Address
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.springframework.context.annotation.Primary
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 테스트용 JavaMailSender 구현체
 * 실제 SMTP 서버를 호출하지 않고, 발송 요청을 큐에 저장하여 검증할 수 있게 함
 */
@Component
@Primary
class FakeJavaMailSender : JavaMailSender {
    private val logger = KotlinLogging.logger {}
    private val sentMessages = ConcurrentLinkedQueue<MimeMessage>()

    init {
        logger.warn { "테스트 환경이 감지되었습니다. FakeJavaMailSender가 활성화되었습니다." }
    }

    override fun send(vararg mimeMessages: MimeMessage) {
        mimeMessages.forEach { sentMessages.add(it) }
    }

    override fun send(simpleMessage: SimpleMailMessage) {
        throw UnsupportedOperationException("SimpleMailMessage is not supported")
    }

    override fun send(vararg simpleMessages: SimpleMailMessage) {
        throw UnsupportedOperationException("SimpleMailMessage is not supported")
    }

    override fun createMimeMessage(): MimeMessage {
        val session = Session.getInstance(Properties())
        return MimeMessage(session)
    }

    override fun createMimeMessage(contentStream: InputStream): MimeMessage {
        val session = Session.getInstance(Properties())
        return MimeMessage(session, contentStream)
    }

    /** 발송된 메시지 목록 조회 */
    fun getSentMessages(): List<MimeMessage> = sentMessages.toList()

    /** 제목으로 이메일 필터링 */
    fun getMessagesBySubject(subject: String): List<MimeMessage> =
        sentMessages.filter { it.subject?.contains(subject) == true }

    /** 제목 패턴으로 이메일 필터링 (정규식) */
    fun getMessagesBySubjectPattern(pattern: Regex): List<MimeMessage> =
        sentMessages.filter { it.subject?.matches(pattern) == true || it.subject?.contains(pattern.pattern) == true }

    /** 수신자 이메일로 필터링 */
    fun getMessagesTo(recipientEmail: String): List<MimeMessage> =
        sentMessages.filter { msg ->
            msg.allRecipients?.any { it.toString() == recipientEmail } == true
        }

    /** 발송된 메시지 개수 */
    fun count(): Int = sentMessages.size

    /** 초기화 */
    fun clear() {
        sentMessages.clear()
    }

    /** 제목 추출 */
    fun extractSubject(message: MimeMessage): String? = message.subject

    /** 수신자 이메일 목록 추출 */
    fun extractRecipients(message: MimeMessage): List<String> =
        message.allRecipients?.map { it.toString() } ?: emptyList()

    /** 첫 번째 수신자 이메일 추출 */
    fun extractFirstRecipient(message: MimeMessage): String? =
        message.allRecipients?.firstOrNull()?.toString()

    /** 발신자 추출 */
    fun extractFrom(message: MimeMessage): Array<Address>? = message.from

    /** 본문 텍스트 추출 (HTML 포함) */
    fun extractContent(message: MimeMessage): String {
        return extractContentFromPart(message.content)
    }

    /** 재귀적으로 내용 추출 */
    private fun extractContentFromPart(content: Any): String {
        return when (content) {
            is String -> content
            is MimeMultipart -> {
                val sb = StringBuilder()
                for (i in 0 until content.count) {
                    val part = content.getBodyPart(i)
                    sb.append(extractContentFromPart(part.content))
                }
                sb.toString()
            }
            else -> content.toString()
        }
    }

    /** 이메일 검증용 데이터 클래스 */
    data class EmailInfo(
        val subject: String?,
        val recipients: List<String>,
        val content: String,
    )

    /** MimeMessage를 검증하기 쉬운 형태로 변환 */
    fun extractEmailInfo(message: MimeMessage): EmailInfo = EmailInfo(
        subject = extractSubject(message),
        recipients = extractRecipients(message),
        content = extractContent(message),
    )

    /** 모든 메시지를 EmailInfo로 변환 */
    fun getAllEmailInfos(): List<EmailInfo> = sentMessages.map { extractEmailInfo(it) }
}

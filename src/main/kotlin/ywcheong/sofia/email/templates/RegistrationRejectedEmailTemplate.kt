package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 신청 거절 이메일 템플릿 (US-028)
 * 신청이 거절될 때 참가자에게 발송
 */
data class RegistrationRejectedEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val rejectionReason: String,
    val rejectedAt: String,
) : EmailTemplate {

    override val templateId: String = "registration-rejected"
    override val subject: String = "가입 신청이 거절되었습니다"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "rejectionReason" to rejectionReason,
        "rejectedAt" to rejectedAt,
        "unsubscribeToken" to unsubscribeToken,
    )
}

package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 신청 승인 이메일 템플릿 (US-028)
 * 신청이 승인될 때 참가자에게 발송
 */
data class RegistrationApprovedEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val approvedAt: String,
) : EmailTemplate {

    override val templateId: String = "registration-approved"
    override val subject: String = "가입 신청이 승인되었습니다"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "approvedAt" to approvedAt,
        "unsubscribeToken" to unsubscribeToken,
    )
}

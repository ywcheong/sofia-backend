package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 경고 발행 이메일 템플릿 (US-027)
 * 경고가 발행될 때 해당 번역버디에게 발송
 */
data class WarningIssuedEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val warningReason: String,
    val warningCount: Int,
    val issuedAt: String,
) : EmailTemplate {

    override val templateId: String = "warning-issued"
    override val subject: String = "번역 과제 경고 발생"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "warningReason" to warningReason,
        "warningCount" to warningCount.toString(),
        "issuedAt" to issuedAt,
        "unsubscribeToken" to unsubscribeToken,
    )
}

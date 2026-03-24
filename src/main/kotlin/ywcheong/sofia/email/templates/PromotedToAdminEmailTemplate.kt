package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 관리자 승급 이메일 템플릿 (US-029)
 * 관리자로 승급될 때 해당 사용자에게 발송
 */
data class PromotedToAdminEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val promotedAt: String,
) : EmailTemplate {

    override val templateId: String = "promoted-to-admin"
    override val subject: String = "관리자 권한이 부여되었습니다"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "promotedAt" to promotedAt,
        "unsubscribeToken" to unsubscribeToken,
    )
}

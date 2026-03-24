package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 관리자 강등 이메일 템플릿 (US-029)
 * 관리자에서 강등될 때 해당 사용자에게 발송
 */
data class DemotedFromAdminEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val demotionReason: String,
    val demotedAt: String,
) : EmailTemplate {

    override val templateId: String = "demoted-from-admin"
    override val subject: String = "관리자 권한이 해제되었습니다"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "demotionReason" to demotionReason,
        "demotedAt" to demotedAt,
        "unsubscribeToken" to unsubscribeToken,
    )
}

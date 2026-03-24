package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 48시간 미제출 관리자 알림 이메일 템플릿 (US-032)
 * 과제가 48시간 경과 미제출 상태일 때 모든 관리자에게 발송
 */
data class TaskReminderAdminNotificationEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val buddyName: String,
    val taskTitle: String,
    val assignedAt: String,
) : EmailTemplate {

    override val templateId: String = "task-reminder-admin-notification"
    override val subject: String = "번역 과제 지연 사용자 알림"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "buddyName" to buddyName,
        "taskTitle" to taskTitle,
        "assignedAt" to assignedAt,
        "unsubscribeToken" to unsubscribeToken,
    )
}

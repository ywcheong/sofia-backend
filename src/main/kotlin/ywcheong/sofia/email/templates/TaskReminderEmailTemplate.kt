package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 48시간 제출 리마인드 이메일 템플릿 (US-032)
 * 과제 할당 후 48시간 내에 제출하지 않았을 때 번역버디에게 발송
 */
data class TaskReminderEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val taskTitle: String,
    val assignedAt: String,
    val taskLink: String,
) : EmailTemplate {

    override val templateId: String = "task-reminder"
    override val subject: String = "번역 과제 마감 임박 알림"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "taskTitle" to taskTitle,
        "assignedAt" to assignedAt,
        "taskLink" to taskLink,
        "unsubscribeToken" to unsubscribeToken,
    )
}

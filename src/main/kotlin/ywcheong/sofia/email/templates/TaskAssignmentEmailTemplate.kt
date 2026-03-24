package ywcheong.sofia.email.templates

import ywcheong.sofia.email.EmailTemplate

/**
 * 과제 할당 이메일 템플릿 (US-026)
 * 과제가 할당될 때 번역버디에게 발송
 */
data class TaskAssignmentEmailTemplate(
    override val recipientEmail: String,
    override val recipientName: String,
    override val unsubscribeToken: String,
    val taskTitle: String,
    val taskDeadline: String,
    val taskLink: String,
) : EmailTemplate {

    override val templateId: String = "task-assignment"
    override val subject: String = "새로운 번역 과제가 할당되었습니다"

    override fun toPlaceholderMap(): Map<String, String> = mapOf(
        "recipientName" to recipientName,
        "taskTitle" to taskTitle,
        "taskDeadline" to taskDeadline,
        "taskLink" to taskLink,
        "unsubscribeToken" to unsubscribeToken,
    )
}

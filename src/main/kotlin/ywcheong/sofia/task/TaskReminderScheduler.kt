package ywcheong.sofia.task

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.email.EmailProperties
import ywcheong.sofia.email.EmailSendService
import ywcheong.sofia.email.templates.TaskReminderAdminNotificationEmailTemplate
import ywcheong.sofia.email.templates.TaskReminderEmailTemplate
import ywcheong.sofia.user.SofiaUserRole
import ywcheong.sofia.user.auth.SofiaUserAuth
import ywcheong.sofia.user.auth.SofiaUserAuthRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
@ConditionalOnProperty(prefix = "sofia.task", name = ["reminder-enabled"], havingValue = "true")
class TaskReminderScheduler(
    private val translationTaskRepository: TranslationTaskRepository,
    private val sofiaUserAuthRepository: SofiaUserAuthRepository,
    private val emailSendService: EmailSendService,
    private val emailProperties: EmailProperties,
    private val taskProperties: TranslationTaskProperties,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"))
    }

    /**
     * 매 1분마다 실행되어 48시간 경과 미완료 과제를 확인하고 리마인더 발송
     * UC-021: 48시간 제출 리마인드
     */
    @Scheduled(cron = "0 * * * * *") // 매 시간 0초에 실행 (매 1분)
    @Transactional
    fun checkAndSendReminders() {

        val reminderThreshold = Instant.now().minusSeconds(taskProperties.lateThresholdSeconds)
        val tasksNeedingReminder = translationTaskRepository.findTasksNeedingReminder(reminderThreshold)

        if (tasksNeedingReminder.isEmpty()) {
            logger.info { "48시간 경과 미제출 과제 검사 완료 (0건)" }
            return
        }

        logger.info { "48시간 경과 미제출 과제 검사 완료 (${tasksNeedingReminder.size}건), 리마인더 발송 시작" }

        val admins = sofiaUserAuthRepository.findAllByRole(SofiaUserRole.ADMIN)

        for (task in tasksNeedingReminder) {
            sendReminderToAssignee(task)
            sendNotificationToAdmins(task, admins)
            task.markAsReminded()
            translationTaskRepository.save(task)
        }

        logger.info { "리마인더 발송 완료: ${tasksNeedingReminder.size}건" }
    }

    private fun sendReminderToAssignee(task: TranslationTask) {
        val assignee = task.assignee
        val assigneeEmail = assignee.email

        val template = TaskReminderEmailTemplate(
            recipientEmail = assigneeEmail.email,
            recipientName = assignee.studentName,
            unsubscribeToken = assigneeEmail.unsubscribeToken.toString(),
            taskTitle = task.taskDescription,
            assignedAt = DATE_TIME_FORMATTER.format(task.assignedAt),
            taskLink = "${emailProperties.baseUrl}/tasks/${task.id}",
        )

        val result = emailSendService.sendEmail(template)
        if (result.success) {
            logger.info { "리마인더 이메일 발송 완료: taskId=${task.id}, to=${assigneeEmail.email}" }
        } else {
            logger.warn { "리마인더 이메일 발송 실패: taskId=${task.id}, to=${assigneeEmail.email}, error=${result.errorMessage}" }
        }
    }

    private fun sendNotificationToAdmins(task: TranslationTask, admins: List<SofiaUserAuth>) {
        val assignee = task.assignee

        for (adminAuth in admins) {
            val admin = adminAuth.user
            val adminEmail = admin.email

            val template = TaskReminderAdminNotificationEmailTemplate(
                recipientEmail = adminEmail.email,
                recipientName = admin.studentName,
                unsubscribeToken = adminEmail.unsubscribeToken.toString(),
                buddyName = assignee.studentName,
                taskTitle = task.taskDescription,
                assignedAt = DATE_TIME_FORMATTER.format(task.assignedAt),
            )

            val result = emailSendService.sendEmail(template)
            if (result.success) {
                logger.info { "관리자 알림 이메일 발송 완료: taskId=${task.id}, to=${adminEmail.email}" }
            } else {
                logger.warn { "관리자 알림 이메일 발송 실패: taskId=${task.id}, to=${adminEmail.email}, error=${result.errorMessage}" }
            }
        }
    }
}

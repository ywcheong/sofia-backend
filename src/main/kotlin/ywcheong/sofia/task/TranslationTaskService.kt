package ywcheong.sofia.task

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.email.EmailProperties
import ywcheong.sofia.email.EmailSendService
import ywcheong.sofia.email.templates.TaskAssignmentEmailTemplate
import ywcheong.sofia.email.templates.WarningIssuedEmailTemplate
import ywcheong.sofia.task.user.SofiaUserRoundRobinService
import ywcheong.sofia.task.user.SofiaUserTaskStatusRepository
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.SofiaUserRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class TranslationTaskService(
    private val translationTaskRepository: TranslationTaskRepository,
    private val sofiaUserRepository: SofiaUserRepository,
    private val sofiaUserRoundRobinService: SofiaUserRoundRobinService,
    private val sofiaUserTaskStatusRepository: SofiaUserTaskStatusRepository,
    private val properties: TranslationTaskProperties,
    private val emailSendService: EmailSendService,
    private val emailProperties: EmailProperties,
) {
    private val logger = KotlinLogging.logger {}

    fun findAllTasks(pageable: Pageable): Page<TranslationTaskController.TaskSummaryResponse> {
        return translationTaskRepository.findAll(pageable).map { task ->
            TranslationTaskController.TaskSummaryResponse(
                id = task.id,
                taskType = task.taskType,
                taskDescription = task.taskDescription,
                assigneeId = task.assignee.id,
                assigneeStudentNumber = task.assignee.studentNumber,
                assigneeName = task.assignee.studentName,
                assignmentType = task.assignmentType,
                assignedAt = formatDateTime(task.assignedAt),
                completed = task.completed,
                characterCount = task.characterCount,
            )
        }
    }

    data class CreateTaskCommand(
        val taskType: TranslationTask.TaskType,
        val taskDescription: String,
        val assignmentType: TranslationTask.AssignmentType,
        val assigneeId: UUID?,
    ) {
        init {
            // BR-007, BR-008: 과제명 길이 검증 (최대 50자)
            if (taskDescription.length > 50) {
                throw BusinessException("과제명은 최대 50자까지 입력 가능합니다.")
            }

            // BR-049: 수동 배정 시 assigneeId 필수
            if (assignmentType == TranslationTask.AssignmentType.MANUAL && assigneeId == null) {
                throw BusinessException("수동 배정 시 번역버디를 지정해야 합니다.")
            }
        }
    }

    data class ReportCompletionCommand(
        val taskId: UUID,
        val characterCount: Int,
    ) {
        init {
            if (characterCount < 0) {
                throw BusinessException("글자 수는 0 이상이어야 합니다.")
            }
        }
    }

    data class ChangeAssigneeCommand(
        val taskId: UUID,
        val newAssigneeId: UUID,
    )

    data class DeleteTaskCommand(
        val taskId: UUID,
    )

    @Transactional
    fun createTask(command: CreateTaskCommand): TranslationTask {
        // BR-006: Work ID 중복 확인
        if (translationTaskRepository.existsByTaskTypeAndTaskDescription(command.taskType, command.taskDescription)) {
            throw BusinessException("이미 존재하는 과제입니다.")
        }

        val assignee = when (command.assignmentType) {
            TranslationTask.AssignmentType.AUTOMATIC -> {
                // BR-048: 자동 배정 - 라운드 로빈 방식
                sofiaUserRoundRobinService.getNextAssignee()
            }

            TranslationTask.AssignmentType.MANUAL -> {
                // BR-049: 수동 배정
                resolveManualAssignee(command.assigneeId!!)
            }
        }

        val task = TranslationTask(
            taskType = command.taskType,
            taskDescription = command.taskDescription,
            assignee = assignee,
            assignmentType = command.assignmentType,
            assignedAt = Instant.now(),
        )

        val savedTask = translationTaskRepository.save(task)

        // US-026: 과제 할당 이메일 발송
        sendTaskAssignmentEmail(savedTask, assignee)

        logger.info { "과제 생성 완료: taskId=${savedTask.id}, assignee=${assignee.studentNumber}" }

        return savedTask
    }

    @Transactional
    fun reportCompletion(command: ReportCompletionCommand): TranslationTask {
        val task = translationTaskRepository.findByIdOrNull(command.taskId)
            ?: throw BusinessException("존재하지 않는 과제입니다.")

        if (task.completed) {
            throw BusinessException("이미 완료된 과제입니다.")
        }

        val now = Instant.now()

        // BR-013: 지각 여부 확인 (48시간 초과)
        val late = ChronoUnit.SECONDS.between(task.assignedAt, now) > properties.lateThresholdSeconds

        task.completedAt = now
        task.characterCount = command.characterCount

        val savedTask = translationTaskRepository.save(task)

        if (late) {
            // BR-014: 지각 시 경고 발행
            val userTask = sofiaUserTaskStatusRepository.findByIdOrNull(task.assignee.id)
                ?: throw IllegalStateException("해당 사용자의 과제 정보가 없습니다: userId=${task.assignee.id}")
            userTask.addWarning()

            logger.warn { "과제 지각으로 경고 발행: taskId=${command.taskId}, userId=${task.assignee.id}, warningCount=${userTask.warningCount}" }

            // US-027: 경고 이메일 발송
            sendWarningEmail(task, userTask.warningCount)
        }

        logger.info { "과제 완료 보고: taskId=${command.taskId}, characterCount=${command.characterCount}, isLate=$late" }

        return savedTask
    }

    @Transactional
    fun changeAssignee(command: ChangeAssigneeCommand): TranslationTask {
        val task = translationTaskRepository.findByIdOrNull(command.taskId)
            ?: throw BusinessException("존재하지 않는 과제입니다.")

        if (task.completed) {
            throw BusinessException("완료된 과제는 담당자를 변경할 수 없습니다.")
        }

        val newAssignee = resolveManualAssignee(command.newAssigneeId)

        task.changeAssignee(newAssignee, TranslationTask.AssignmentType.MANUAL)
        val savedTask = translationTaskRepository.save(task)

        // 담당자 변경 알림 이메일 발송
        sendTaskAssignmentEmail(savedTask, newAssignee)

        logger.info { "과제 담당자 변경: taskId=${command.taskId}, newAssignee=${newAssignee.studentNumber}" }

        return savedTask
    }

    @Transactional
    fun deleteTask(command: DeleteTaskCommand) {
        val task = translationTaskRepository.findByIdOrNull(command.taskId)
            ?: throw BusinessException("존재하지 않는 과제입니다.")

        translationTaskRepository.delete(task)

        logger.info { "과제 삭제: taskId=${command.taskId}" }
    }

    // BR-012: 봉사시간 계산 (1글자 = 3.942초)
    fun calculateEstimatedServiceTimeSeconds(characterCount: Int): Double {
        return characterCount * properties.secondsPerCharacter
    }

    fun isLate(task: TranslationTask): Boolean {
        val completedAt = task.completedAt ?: return false
        return ChronoUnit.SECONDS.between(task.assignedAt, completedAt) > properties.lateThresholdSeconds
    }

    private fun resolveManualAssignee(assigneeId: UUID): SofiaUser {
        val user = sofiaUserRepository.findByIdOrNull(assigneeId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        val userTask = sofiaUserTaskStatusRepository.findByIdOrNull(assigneeId)
            ?: throw IllegalStateException("해당 사용자의 과제 정보가 없습니다.")

        if (userTask.rest) {
            throw BusinessException("휴식 상태인 번역버디에게는 과제를 할당할 수 없습니다.")
        }

        return user
    }

    private fun sendTaskAssignmentEmail(task: TranslationTask, assignee: SofiaUser) {
        val deadline = task.assignedAt.plus(properties.lateThresholdSeconds, ChronoUnit.SECONDS)
        val formattedDeadline = formatDateTime(deadline)
        val taskLink = "${emailProperties.baseUrl}/tasks/${task.id}"

        val template = TaskAssignmentEmailTemplate(
            recipientEmail = assignee.email.email,
            recipientName = assignee.studentName,
            unsubscribeToken = assignee.email.unsubscribeToken.toString(),
            taskTitle = task.taskDescription,
            taskDeadline = formattedDeadline,
            taskLink = taskLink,
        )

        val result = emailSendService.sendEmail(template)
        if (!result.success && result.errorMessage == "Recipient has unsubscribed from emails") {
            logger.info { "과제 할당 이메일 수신거부: userId=${assignee.id}" }
        }
    }

    private fun sendWarningEmail(task: TranslationTask, warningCount: Int) {
        val assignee = task.assignee
        val warningReason = "과제 '${task.taskDescription}'가 48시간 내에 제출되지 않았습니다."

        val template = WarningIssuedEmailTemplate(
            recipientEmail = assignee.email.email,
            recipientName = assignee.studentName,
            unsubscribeToken = assignee.email.unsubscribeToken.toString(),
            warningReason = warningReason,
            warningCount = warningCount,
            issuedAt = formatDateTime(Instant.now()),
        )

        val result = emailSendService.sendEmail(template)
        if (!result.success && result.errorMessage == "Recipient has unsubscribed from emails") {
            logger.info { "경고 이메일 수신거부: userId=${assignee.id}" }
        }
    }

    private fun formatDateTime(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"))
        return formatter.format(instant)
    }
}

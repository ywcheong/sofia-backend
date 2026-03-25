package ywcheong.sofia.task

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.auth.SofiaPermission
import java.util.UUID

@RestController
@RequestMapping("/tasks")
@Tag(name = "Translation Task", description = "번역 과제 생성, 조회, 완료 보고 및 성과 보고서 생성 API")
class TranslationTaskController(
    private val translationTaskService: TranslationTaskService,
    private val translationTaskCsvExportService: TranslationTaskCsvExportService,
) {
    private val logger = KotlinLogging.logger {}

    // 과제 목록 조회
    data class TaskSummaryResponse(
        val id: UUID,
        val taskType: TranslationTask.TaskType,
        val taskDescription: String,
        val assigneeId: UUID,
        val assigneeStudentNumber: String,
        val assigneeName: String,
        val assignmentType: TranslationTask.AssignmentType,
        val assignedAt: String,
        val completed: Boolean,
        val characterCount: Int?,
    )

    @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping
    fun findAllTasks(pageable: Pageable): Page<TaskSummaryResponse> {
        logger.info { "과제 목록 조회 요청: page=${pageable.pageNumber}, size=${pageable.pageSize}" }
        return translationTaskService.findAllTasks(pageable)
    }

    // UC-003: 번역 과제 생성
    data class CreateTaskRequest(
        val taskType: TranslationTask.TaskType,
        val taskDescription: String,
        val assignmentType: TranslationTask.AssignmentType,
        val assigneeId: UUID?, // 수동 배정 시에만 사용
    )

    data class CreateTaskResponse(
        val taskId: UUID,
        val assigneeId: UUID,
        val assigneeStudentNumber: String,
        val assigneeName: String,
    )

    @AvailableCondition(phases = [SystemPhase.TRANSLATION], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PostMapping
    fun createTask(@RequestBody request: CreateTaskRequest): CreateTaskResponse {
        logger.info { "과제 생성 요청: taskType=${request.taskType}, taskDescription=${request.taskDescription}" }

        val command = TranslationTaskService.CreateTaskCommand(
            taskType = request.taskType,
            taskDescription = request.taskDescription,
            assignmentType = request.assignmentType,
            assigneeId = request.assigneeId,
        )

        val task = translationTaskService.createTask(command)

        return CreateTaskResponse(
            taskId = task.id,
            assigneeId = task.assignee.id,
            assigneeStudentNumber = task.assignee.studentNumber,
            assigneeName = task.assignee.studentName,
        )
    }

    // UC-004: 과제 완료 보고
    data class ReportCompletionRequest(
        val characterCount: Int,
    )

    data class ReportCompletionResponse(
        val taskId: UUID,
        val late: Boolean,
    )

    @AvailableCondition(phases = [SystemPhase.TRANSLATION], permissions = [SofiaPermission.KAKAO_ENDPOINT])
    @PostMapping("/{taskId}/completion")
    fun reportCompletion(
        @PathVariable taskId: UUID,
        @RequestBody request: ReportCompletionRequest,
    ): ReportCompletionResponse {
        logger.info { "과제 완료 보고 요청: taskId=$taskId, characterCount=${request.characterCount}" }

        val command = TranslationTaskService.ReportCompletionCommand(
            taskId = taskId,
            characterCount = request.characterCount,
        )

        val task = translationTaskService.reportCompletion(command)

        return ReportCompletionResponse(
            taskId = task.id,
            late = translationTaskService.isLate(task),
        )
    }

    // UC-011: 성과 보고서 생성
    @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping("/reports/performance.csv", produces = ["text/csv; charset=UTF-8"])
    fun generatePerformanceReport(): ResponseEntity<String> {
        logger.info { "성과 보고서 생성 요청" }

        val reports = translationTaskCsvExportService.generatePerformanceReport()
        val csv = translationTaskCsvExportService.toCsv(reports)

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=performance_report.csv")
            .body(csv)
    }
}

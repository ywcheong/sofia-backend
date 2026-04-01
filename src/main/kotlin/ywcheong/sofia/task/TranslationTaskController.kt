package ywcheong.sofia.task

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.commons.PageResponse
import ywcheong.sofia.commons.SortDirection
import ywcheong.sofia.commons.SortRequest
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
        val late: Boolean,
        val remindedAt: String?,
    )

    @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping
    @Operation(summary = "과제 목록 조회", description = "과제 설명, 타입, 배정타입, 완료상태, 담당자로 필터링 가능. 정렬 필드: id, assignedAt, completedAt, characterCount")
    fun findAllTasks(
        pageable: Pageable,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) taskType: TranslationTask.TaskType?,
        @RequestParam(required = false) assignmentType: TranslationTask.AssignmentType?,
        @RequestParam(required = false) completed: Boolean?,
        @RequestParam(required = false) assigneeId: UUID?,
        @RequestParam(required = false) sortField: String?,
        @RequestParam(required = false) sortDirection: SortDirection?,
    ): PageResponse<TaskSummaryResponse> {
        logger.info { "과제 목록 조회 요청: page=${pageable.pageNumber}, size=${pageable.pageSize}, search=$search, taskType=$taskType, assignmentType=$assignmentType, completed=$completed, assigneeId=$assigneeId, sortField=$sortField, sortDirection=$sortDirection" }

        val sortRequest = SortRequest(sortField, sortDirection)
        sortRequest.validate()

        val condition = TranslationTaskService.FindAllTasksCondition(
            search = search,
            taskType = taskType,
            assignmentType = assignmentType,
            completed = completed,
            assigneeId = assigneeId,
        )

        return PageResponse.from(translationTaskService.findAllTasks(condition, pageable, sortRequest))
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
    @Operation(summary = "번역 과제 생성", description = "assigneeId=null이면 자동배정, 아니면 지정된 사용자에게 수동배정")
    fun createTask(
        @RequestBody request: CreateTaskRequest,
    ): CreateTaskResponse {
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

    // 번역 과제 CSV 다운로드
    @AvailableCondition(phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping("/csv", produces = ["text/csv; charset=UTF-8"])
    @Operation(summary = "번역 과제 CSV 다운로드", description = "모든 번역 과제 데이터를 CSV 포맷으로 다운로드")
    fun downloadCsv(): ResponseEntity<String> {
        logger.info { "번역 과제 CSV 다운로드 요청" }

        val csv = translationTaskCsvExportService.generateCsv()

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=tasks.csv")
            .body(csv)
    }

    // 담당자 변경
    data class ChangeAssigneeRequest(
        val newAssigneeId: UUID,
    )

    data class ChangeAssigneeResponse(
        val taskId: UUID,
        val newAssigneeId: UUID,
        val newAssigneeStudentNumber: String,
        val newAssigneeName: String,
    )

    @AvailableCondition(phases = [SystemPhase.TRANSLATION], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PatchMapping("/{taskId}/assignee")
    @Operation(summary = "과제 담당자 변경", description = "완료되지 않은 과제의 담당자를 변경")
    fun changeAssignee(
        @PathVariable taskId: UUID,
        @RequestBody request: ChangeAssigneeRequest,
    ): ChangeAssigneeResponse {
        logger.info { "과제 담당자 변경 요청: taskId=$taskId, newAssigneeId=${request.newAssigneeId}" }

        val command = TranslationTaskService.ChangeAssigneeCommand(
            taskId = taskId,
            newAssigneeId = request.newAssigneeId,
        )

        val task = translationTaskService.changeAssignee(command)

        return ChangeAssigneeResponse(
            taskId = task.id,
            newAssigneeId = task.assignee.id,
            newAssigneeStudentNumber = task.assignee.studentNumber,
            newAssigneeName = task.assignee.studentName,
        )
    }

    // 과제 삭제
    @AvailableCondition(phases = [SystemPhase.TRANSLATION], permissions = [SofiaPermission.ADMIN_LEVEL])
    @DeleteMapping("/{taskId}")
    @Operation(summary = "과제 삭제", description = "과제 ID로 과제 삭제")
    fun deleteTask(
        @PathVariable taskId: UUID,
    ): ResponseEntity<Unit> {
        logger.info { "과제 삭제 요청: taskId=$taskId" }

        val command = TranslationTaskService.DeleteTaskCommand(taskId)
        translationTaskService.deleteTask(command)

        return ResponseEntity.noContent().build()
    }
}

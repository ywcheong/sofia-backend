package ywcheong.sofia.task

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class TranslationTaskCsvExportService(
    private val translationTaskRepository: TranslationTaskRepository,
    private val properties: TranslationTaskProperties,
) {
    private val logger = KotlinLogging.logger {}

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.of("Asia/Seoul"))

    @Transactional(readOnly = true)
    fun generateCsv(): String {
        logger.info { "과제 CSV 생성 시작" }

        val tasks = translationTaskRepository.findAll()

        val header = "과제유형,과제설명,담당자학번,담당자이름,배정유형,배정일시,완료여부,자수,지각여부,리마인드일시"
        val rows = tasks.map { task ->
            val taskTypeName = when (task.taskType) {
                TranslationTask.TaskType.GAONNURI_POST -> "가온누리 게시글"
                TranslationTask.TaskType.EXTERNAL_POST -> "외부 게시글"
            }
            val assignmentTypeName = when (task.assignmentType) {
                TranslationTask.AssignmentType.AUTOMATIC -> "자동"
                TranslationTask.AssignmentType.MANUAL -> "수동"
            }
            val assignedAtFormatted = formatDateTime(task.assignedAt)
            val completedText = if (task.completed) "예" else "아니오"
            val characterCountText = task.characterCount?.toString() ?: ""
            val late = isLate(task)
            val lateText = if (late) "예" else "아니오"
            val remindedAtText = task.remindedAt?.let { formatDateTime(it) } ?: ""

            "$taskTypeName,${task.taskDescription},${task.assignee.studentNumber},${task.assignee.studentName},$assignmentTypeName,$assignedAtFormatted,$completedText,$characterCountText,$lateText,$remindedAtText"
        }

        val csvContent = (listOf(header) + rows).joinToString("\n")
        val bom = "\uFEFF"

        logger.info { "과제 CSV 생성 완료: ${tasks.size}건" }
        return bom + csvContent
    }

    private fun formatDateTime(instant: Instant): String {
        return dateTimeFormatter.format(instant)
    }

    private fun isLate(task: TranslationTask): Boolean {
        val completedAt = task.completedAt ?: return false
        return ChronoUnit.SECONDS.between(task.assignedAt, completedAt) > properties.lateThresholdSeconds
    }
}

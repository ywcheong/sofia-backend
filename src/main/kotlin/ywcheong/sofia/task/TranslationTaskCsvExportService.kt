package ywcheong.sofia.task

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.task.user.SofiaUserTaskStatusRepository
import ywcheong.sofia.user.SofiaUserRepository

@Service
class TranslationTaskCsvExportService(
    private val translationTaskRepository: TranslationTaskRepository,
    private val sofiaUserRepository: SofiaUserRepository,
    private val sofiaUserTaskStatusRepository: SofiaUserTaskStatusRepository,
    private val properties: TranslationTaskProperties,
) {
    private val logger = KotlinLogging.logger {}

    data class UserPerformanceReport(
        val studentNumber: String,
        val studentName: String,
        val translatedCharacterCount: Int,       // 번역 자수
        val adjustedCharacterCount: Int,         // 보정 자수
        val totalCharacterCount: Int,            // 보정후 번역자수 (번역 자수 + 보정 자수)
        val warningCount: Int,
        val secondsPerCharacter: Double,
    ) {
        // BR-012: 1글자 = 3.942초, 예상 봉사시간 = (번역 자수 + 보정 자수) × 3.942초
        val estimatedServiceTimeSeconds: Double
            get() = totalCharacterCount * secondsPerCharacter
    }

    @Transactional(readOnly = true)
    fun generatePerformanceReport(): List<UserPerformanceReport> {
        logger.info { "성과 보고서 생성 시작" }

        val users = sofiaUserRepository.findAll()
        val reports = users.map { user ->
            val completedTasks = translationTaskRepository.findByAssigneeAndCompletedAtIsNotNull(user)
            val totalCharacterCount = completedTasks.sumOf { it.characterCount ?: 0 }
            val userTaskStatus = sofiaUserTaskStatusRepository.findById(user.id).orElse(null)

            UserPerformanceReport(
                studentNumber = user.studentNumber,
                studentName = user.studentName,
                translatedCharacterCount = totalCharacterCount,
                adjustedCharacterCount = 0, // TODO: 보정 자수 기능 구현 후 연결
                totalCharacterCount = totalCharacterCount, // TODO: 보정 자수 기능 구현 후 adjustedCharacterCount와 합산 필요
                warningCount = userTaskStatus?.warningCount ?: 0,
                secondsPerCharacter = properties.secondsPerCharacter,
            )
        }

        logger.info { "성과 보고서 생성 완료: ${reports.size}명" }
        return reports
    }

    fun toCsv(reports: List<UserPerformanceReport>): String {
        val header = "학번,이름,번역 자수,보정 자수,경고 횟수,예상 봉사시간(초)"
        val rows = reports.map { report ->
            "${report.studentNumber},${report.studentName},${report.translatedCharacterCount},${report.adjustedCharacterCount},${report.warningCount},${report.estimatedServiceTimeSeconds}"
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}

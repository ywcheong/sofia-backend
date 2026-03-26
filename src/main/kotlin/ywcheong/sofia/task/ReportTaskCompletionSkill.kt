package ywcheong.sofia.task

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.task.user.SofiaUserTaskStatusRepository
import ywcheong.sofia.user.SofiaUser

@Service
class ReportTaskCompletionSkill(
    private val translationTaskService: TranslationTaskService,
    private val translationTaskRepository: TranslationTaskRepository,
    private val sofiaUserTaskStatusRepository: SofiaUserTaskStatusRepository,
) : KakaoSkill<ReportTaskCompletionSkill.ActionData>(
    responsibleAction = "reportwork",
    actionDataType = ActionData::class,
    allowedPhases = listOf(SystemPhase.TRANSLATION),
) {
    data class ActionData(
        val taskDescription: String,
        val characterCount: String,
    )

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: ActionData,
    ): KakaoSkillResult {
        // 1. 인증되지 않은 사용자 체크
        if (user == null) {
            throw BusinessException("가입된 번역버디만 사용할 수 있습니다.")
        }

        // 2. 글자 수 파싱
        val characterCount = parseCharacterCount(params.characterCount) ?: throw BusinessException("글자 수는 숫자여야 합니다.")
        if (characterCount < 0) {
            throw BusinessException("글자 수는 0 이상이어야 합니다.")
        }

        // 3. 과제 조회
        val task = translationTaskRepository.findByTaskDescription(params.taskDescription)
            ?: throw BusinessException("존재하지 않는 과제입니다.")

        // 4. 과제 완료 보고
        val command = TranslationTaskService.ReportCompletionCommand(
            taskId = task.id,
            characterCount = characterCount,
        )
        val updatedTask = translationTaskService.reportCompletion(command)
        val isLate = translationTaskService.isLate(updatedTask)

        // 5. 총 글자수 및 경고수 조회
        val taskCharCount = translationTaskRepository.sumCharacterCountByAssignee(user)
        val userTaskStatus = sofiaUserTaskStatusRepository.findByIdOrNull(user.id)
        val adjustedCharCount = userTaskStatus?.adjustedCharCount ?: 0
        val totalCharCount = taskCharCount + adjustedCharCount
        val warningCount = userTaskStatus?.warningCount ?: 0

        // 6. 응답 메시지 생성
        val message = buildResultMessage(isLate, totalCharCount, warningCount)

        return KakaoSkillResult(
            message = message,
            quickReplies = listOf(KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴")),
        )
    }

    private fun parseCharacterCount(input: String): Int? {
        // "자" 접미사 제거
        val trimmed = input.trim().removeSuffix("자").trim()
        return trimmed.toIntOrNull()
    }

    private fun buildResultMessage(isLate: Boolean, totalCharCount: Int, warningCount: Int): String {
        val baseMessage = "번역 보고가 완료되었습니다. 현재까지 ${totalCharCount}자를 번역했습니다."

        if (isLate) {
            return "$baseMessage\n\n[경고 안내] 이 작업은 기한보다 늦게 제출되었습니다. 자동으로 경고 1회가 등록되었습니다(현재 ${warningCount}개). 주의하세요."
        }

        return baseMessage
    }
}

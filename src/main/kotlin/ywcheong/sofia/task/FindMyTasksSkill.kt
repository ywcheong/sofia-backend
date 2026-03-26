package ywcheong.sofia.task

import org.springframework.stereotype.Service
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser
import java.time.Instant

@Service
class FindMyTasksSkill(
    private val translationTaskRepository: TranslationTaskRepository,
    private val translationTaskProperties: TranslationTaskProperties,
) : KakaoSkill<FindMyTasksSkill.NoParams>(
    responsibleAction = "givenwork",
    actionDataType = NoParams::class,
    allowedPhases = listOf(SystemPhase.TRANSLATION),
) {
    data object NoParams

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: NoParams,
    ): KakaoSkillResult {
        // 인증되지 않은 사용자 체크
        if (user == null) {
            return KakaoSkillResult(
                message = "가입된 번역버디만 사용할 수 있습니다.",
                quickReplies = listOf(
                    KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                ),
            )
        }

        // 미완료 과제 조회 (오래된 순)
        val incompleteTasks = translationTaskRepository.findByAssigneeAndCompletedAtIsNullOrderByAssignedAtAsc(user)

        // 과제가 없으면
        if (incompleteTasks.isEmpty()) {
            return KakaoSkillResult(
                message = "현재 배정된 업무가 없습니다.",
                quickReplies = listOf(
                    KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                ),
            )
        }

        // 과제가 있으면 메시지 구성
        val message = buildString {
            appendLine("현재 해결해야 하는 업무는 다음과 같습니다.")
            appendLine()

            incompleteTasks.forEach { task ->
                val taskTypeText = when (task.taskType) {
                    TranslationTask.TaskType.GAONNURI_POST -> "가온누리"
                    TranslationTask.TaskType.EXTERNAL_POST -> "외부"
                }

                val elapsedTimeSeconds = Instant.now().epochSecond - task.assignedAt.epochSecond
                val elapsedTimeText = formatElapsedTime(elapsedTimeSeconds)

                val isLate = elapsedTimeSeconds > translationTaskProperties.lateThresholdSeconds
                val lateWarning = if (isLate) {
                    "\n[마감기한이 지난 업무이므로 임의로 삭제될 수 있습니다. 번역버디장과 연락하세요.]"
                } else {
                    ""
                }

                appendLine("- ${task.taskDescription} ($taskTypeText, $elapsedTimeText)$lateWarning")
            }
        }

        return KakaoSkillResult(
            message = message.trimEnd(),
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                KakaoSkillResult.QuickReply(label = "끝난 번역 보고하기", messageText = "번역보고"),
            ),
        )
    }

    private fun formatElapsedTime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60

        return buildString {
            if (days > 0) append("${days}일 ")
            if (hours > 0) append("${hours}시간 ")
            if (minutes > 0 && days == 0L) append("${minutes}분") // 1일 이상이면 분 생략
            if (isEmpty()) append("방금")
        }.trim()
    }
}

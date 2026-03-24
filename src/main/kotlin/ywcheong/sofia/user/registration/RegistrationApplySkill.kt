package ywcheong.sofia.user.registration

import org.springframework.stereotype.Service
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser

@Service
class RegistrationApplySkill(
    private val userRegistrationService: UserRegistrationService,
) : KakaoSkill<RegistrationApplySkill.ActionData>(
    responsibleAction = "registration_apply",
    actionDataType = ActionData::class,
    allowedPhases = listOf(SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION),
) {
    data class ActionData(
        val studentNumber: String,
        val studentName: String,
    )

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: ActionData,
    ): KakaoSkillResult {
        // 이미 가입된 사용자인지 확인
        if (user != null) {
            return KakaoSkillResult(
                message = "이미 가입을 요청했거나, 또는 이미 가입된 상태입니다.",
                quickReplies = listOf(
                    KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "home"),
                ),
            )
        }

        val command = UserRegistrationService.ApplyCommand(
            studentNumber = params.studentNumber,
            studentName = params.studentName,
            plusfriendUserKey = plusFriendUserKey,
        )

        userRegistrationService.applyRegistration(command)

        return KakaoSkillResult(
            message = """
                번역버디 권한을 신청했습니다. 관리자의 승인을 받으면 번역기간부터 활동이 가능합니다.
                학번: ${params.studentNumber}
                이름: ${params.studentName}
            """.trimIndent(),
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "home"),
                KakaoSkillResult.QuickReply(label = "가입요청 취소하기", messageText = "registration_cancel_confirm"),
            ),
        )
    }
}

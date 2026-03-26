package ywcheong.sofia.user.registration

import org.springframework.stereotype.Service
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser

@Service
class RegistrationCancelSkill(
    private val userRegistrationService: UserRegistrationService,
) : KakaoSkill<RegistrationCancelSkill.ActionData>(
    responsibleAction = "registration_cancel",
    actionDataType = ActionData::class,
    allowedPhases = listOf(SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION),
) {
    data class ActionData(
        val sure: String?,
    )

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: ActionData,
    ): KakaoSkillResult {
        // 확인 메시지를 받지 않은 경우
        if (params.sure != "네, 취소합니다.") {
            return KakaoSkillResult(
                message = "네, 알겠습니다. 가입 요청을 철회하지 않겠습니다.",
                quickReplies = listOf(
                    KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                ),
            )
        }

        userRegistrationService.cancelRegistrationByPlusfriendUserKey(plusFriendUserKey)

        return KakaoSkillResult(
            message = "요청이 삭제되었습니다.",
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                KakaoSkillResult.QuickReply(label = "가입 요청하기", messageText = "가입요청"),
            ),
        )
    }
}

package ywcheong.sofia.user.registration

import org.springframework.stereotype.Service
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser

@Service
class RegistrationCancelConfirmSkill : KakaoSkill<RegistrationCancelConfirmSkill.NoParams>(
    responsibleAction = "registration_cancel_confirm",
    actionDataType = NoParams::class,
    allowedPhases = listOf(SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION),
) {
    data object NoParams

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: NoParams,
    ): KakaoSkillResult {
        return KakaoSkillResult(
            message = "정말로 가입 요청을 취소하시겠습니까?",
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "네, 취소합니다.", messageText = "registration_cancel sure=네, 취소합니다."),
                KakaoSkillResult.QuickReply(label = "아니오", messageText = "home"),
            ),
        )
    }
}

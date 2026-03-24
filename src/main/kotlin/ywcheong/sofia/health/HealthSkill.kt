package ywcheong.sofia.health

import org.springframework.stereotype.Service
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.user.SofiaUser

@Service
class HealthSkill : KakaoSkill<HealthSkill.NoParams>(
    responsibleAction = "healthcheck",
    actionDataType = NoParams::class,
    allowedPhases = listOf() // 전체허용
) {
    data object NoParams

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: NoParams,
    ): KakaoSkillResult {
        return KakaoSkillResult(
            message = "ok",
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "재시도", messageText = "healthcheck"),
                KakaoSkillResult.QuickReply(label = "홈으로", messageText = "home"),
            ),
        )
    }
}

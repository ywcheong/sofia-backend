package ywcheong.sofia.user.auth

import org.springframework.stereotype.Service
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser

@Service
class RegenerateTokenSkill(
    private val sofiaUserAuthService: SofiaUserAuthService,
) : KakaoSkill<RegenerateTokenSkill.RegenerateAction>(
    responsibleAction = "retoken",
    actionDataType = RegenerateAction::class,
    allowedPhases = listOf(
        SystemPhase.RECRUITMENT,
        SystemPhase.TRANSLATION,
        SystemPhase.SETTLEMENT,
    ),
) {
    data class RegenerateAction(
        val sure: String,
    )

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: RegenerateAction,
    ): KakaoSkillResult {
        val verifiedUser = user ?: throw BusinessException("등록된 사용자만 이용할 수 있습니다.")

        if (params.sure != CONFIRMATION_TEXT) {
            return KakaoSkillResult(
                message = "네, 재발급 절차를 취소했습니다.",
                quickReplies = listOf(
                    KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                ),
            )
        }

        val newToken = sofiaUserAuthService.regenerateToken(verifiedUser)

        val message = buildString {
            appendLine("재발급되었습니다.")
            append(newToken)
        }

        return KakaoSkillResult(
            message = message,
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
            ),
        )
    }

    companion object {
        const val CONFIRMATION_TEXT = "예, 재발급합니다."
    }
}

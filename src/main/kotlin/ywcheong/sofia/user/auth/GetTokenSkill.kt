package ywcheong.sofia.user.auth

import org.springframework.stereotype.Service
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser

@Service
class GetTokenSkill : KakaoSkill<GetTokenSkill.NoParams>(
    responsibleAction = "gettoken",
    actionDataType = NoParams::class,
    allowedPhases = listOf(
        SystemPhase.RECRUITMENT,
        SystemPhase.TRANSLATION,
        SystemPhase.SETTLEMENT,
    ),
) {
    data object NoParams

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: NoParams,
    ): KakaoSkillResult {
        val verifiedUser = user ?: throw BusinessException("등록된 사용자만 이용할 수 있습니다.")

        val message = buildString {
            appendLine("이 토큰을 이용하면 Sofia 페이지에 로그인이 가능합니다.")
            appendLine("아래의 토큰을 복사 후 붙여넣으세요.")
            appendLine()
            append(verifiedUser.auth.secretToken)
        }

        return KakaoSkillResult(
            message = message,
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                KakaoSkillResult.QuickReply(label = "토큰 도움말 보기", messageText = "토큰 도움말"),
                KakaoSkillResult.QuickReply(label = "토큰 재발급하기", messageText = "retoken"),
            ),
        )
    }
}

package ywcheong.sofia.user

import org.springframework.stereotype.Service
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase

@Service
class MyInfoSkill(
    private val myInfoService: MyInfoService,
) : KakaoSkill<MyInfoSkill.NoParams>(
    responsibleAction = "myinfo",
    actionDataType = NoParams::class,
    allowedPhases = listOf(SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT),
) {
    data object NoParams

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: NoParams,
    ): KakaoSkillResult {
        // 승인된 사용자인 경우
        if (user != null) {
            return buildApprovedUserInfo(user)
        }

        // 가입 요청 심사 중인지 확인
        val registration = myInfoService.findPendingRegistration(plusFriendUserKey)
        if (registration != null) {
            return KakaoSkillResult(
                message = "현재 귀하의 가입 요청이 심사 중입니다. 승인되면 번역버디라는 안내가 추가됩니다. 거부되면 가입 요청이 자동으로 삭제됩니다.",
                quickReplies = listOf(
                    KakaoSkillResult.QuickReply(label = "가입요청 취소하기", messageText = "가입취소"),
                    KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                ),
            )
        }

        // 미가입자
        return KakaoSkillResult(
            message = "현재 가입 요청을 하지 않았습니다.",
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "가입 요청하기", messageText = "가입요청"),
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
            ),
        )
    }

    private fun buildApprovedUserInfo(user: SofiaUser): KakaoSkillResult {
        val info = myInfoService.getMyInfo(user)

        val message = buildString {
            appendLine("안녕하세요, ${info.studentNumber} ${info.studentName}님, 당신은 ${info.roleText}입니다.")
            appendLine()
            appendLine("번역한 글자 수: ${info.completedCharCount} (${if (info.adjustedCharCount >= 0) "+" else ""}${info.adjustedCharCount})")
            appendLine("봉사 시간(추정): ${info.formatEstimatedDuration()}")
            appendLine("받은 경고: ${info.warningCount}개")
            append("* 작업으로 받은 글자수 (+특별 부여된 글자수)")
        }

        return KakaoSkillResult(
            message = message,
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                KakaoSkillResult.QuickReply(label = "토큰 조회하기", messageText = "토큰조회"),
            ),
        )
    }
}

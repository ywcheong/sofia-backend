package ywcheong.sofia.glossary

import org.springframework.stereotype.Service
import ywcheong.sofia.kakao.KakaoSkill
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser

@Service
class GlossaryDictionarySkill(
    private val glossaryService: GlossaryService,
) : KakaoSkill<GlossaryDictionarySkill.DictionaryAction>(
    responsibleAction = "dictionary",
    actionDataType = DictionaryAction::class,
    allowedPhases = listOf(
        SystemPhase.RECRUITMENT,
        SystemPhase.TRANSLATION,
        SystemPhase.SETTLEMENT,
    ),
) {
    data class DictionaryAction(
        val text: String,
    )

    override fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: DictionaryAction,
    ): KakaoSkillResult {
        val text = params.text
        val totalLength = text.length
        val trimmedLength = text.filter { !it.isWhitespace() }.length

        val mappedTerms = glossaryService.autoMap(text)
        val foundCount = mappedTerms.size

        val message = buildString {
            appendLine("[분석 결과]")
            appendLine("분석한 텍스트: \"${truncateText(text)}\"")
            appendLine("텍스트의 길이(전체): ${totalLength}자")
            appendLine("텍스트의 길이(공백/개행 등 제외): ${trimmedLength}자")
            appendLine("번역 가이드라인에서 찾은 단어: ${foundCount}개")
            appendLine()

            if (mappedTerms.isEmpty()) {
                appendLine("제공하신 텍스트에서 번역 가이드라인에 포함된 단어를 찾지 못했습니다.")
            } else {
                appendLine("찾은 단어 목록:")
                mappedTerms.forEach { term ->
                    appendLine("- ${term.koreanTerm} → ${term.englishTerm}")
                }
            }
        }

        return KakaoSkillResult(
            message = message.trimEnd(),
            quickReplies = listOf(
                KakaoSkillResult.QuickReply(label = "홈 메뉴", messageText = "홈 메뉴"),
                KakaoSkillResult.QuickReply(label = "다시 찾기", messageText = "번역 도우미"),
            ),
        )
    }

    private fun truncateText(text: String, maxLength: Int = 50): String {
        return if (text.length > maxLength) {
            "${text.take(maxLength)} ..."
        } else {
            text
        }
    }
}

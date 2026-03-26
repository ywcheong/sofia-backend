package ywcheong.sofia.kakao

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.kakao.KakaoSkillController.SkillResponse

/**
 * Kakao Skill 관련 예외를 처리하여 SkillResponse 형식으로 변환합니다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Tag(name = "Kakao Skill Exception Handler", description = "카카오톡 스킬 예외 처리기")
class KakaoSkillExceptionHandler {
    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(KakaoSkillException::class)
    fun handleKakaoSkillException(ex: KakaoSkillException): KakaoSkillController.SkillResponse {
        when (val cause = ex.cause) {
            is BusinessException -> logger.warn { "Kakao Skill 비즈니스 오류: ${cause.message}" }
            else -> logger.error(cause) { "Kakao Skill 장애 발생: ${cause?.message}" }
        }

        return SkillResponse(
            template = SkillResponse.Template(
                outputs = listOf(
                    SkillResponse.Output(
                        simpleText = SkillResponse.Output.SimpleText(text = ex.userMessage)
                    )
                ),
                quickReplies = listOf(
                    SkillResponse.QuickReply(
                        action = "message",
                        label = "홈 메뉴",
                        messageText = "홈 메뉴",
                    )
                ),
            )
        )
    }
}

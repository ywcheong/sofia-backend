package ywcheong.sofia.kakao

import ywcheong.sofia.commons.BusinessException

/**
 * Kakao Skill 처리 중 발생한 예외를 래핑하는 예외 클래스.
 * KakaoSkillExceptionHandler에서 이 예외를 잡아 처리합니다.
 */
class KakaoSkillException(
    val userMessage: String,
    cause: Throwable? = null,
) : RuntimeException(userMessage, cause) {
    companion object {
        /**
         * BusinessException을 KakaoSkillException으로 래핑합니다.
         * 사용자에게 보여줄 메시지는 BusinessException의 message를 사용합니다.
         */
        fun fromBusinessException(ex: BusinessException): KakaoSkillException {
            return KakaoSkillException(
                userMessage = ex.message,
                cause = ex,
            )
        }

        /**
         * 시스템 장애 예외를 KakaoSkillException으로 래핑합니다.
         * 사용자에게는 일반적인 서버 오류 메시지를 보여줍니다.
         */
        fun fromSystemError(ex: Exception): KakaoSkillException {
            return KakaoSkillException(
                userMessage = "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                cause = ex,
            )
        }
    }
}

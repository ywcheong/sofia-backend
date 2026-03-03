package ywcheong.sofia.kakao.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class KakaoSkillSecretFilter(
    @Value("\${KAKAO_SKILL_SECRET:}")
    private val kakaoSkillSecret: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/kakao/skill/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val providedSecret = request.getHeader(HEADER_NAME)
        if (providedSecret != kakaoSkillSecret) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER_NAME = "X-SOFIA-SKILL-SECRET"
    }
}

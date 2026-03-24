package ywcheong.sofia.config.security

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ywcheong.sofia.kakao.KakaoPrincipal
import ywcheong.sofia.kakao.KakaoSkillSecretProperties
import ywcheong.sofia.user.SofiaUserRole
import ywcheong.sofia.user.auth.SofiaActor
import ywcheong.sofia.user.auth.SofiaUserAuthRepository
import java.util.*

@Component
class ApiKeyAuthFilter(
    private val userAuthRepository: SofiaUserAuthRepository,
    private val kakaoSkillSecretProperties: KakaoSkillSecretProperties,
) : OncePerRequestFilter() {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val USER_PREFIX = "user"
        private const val KAKAO_PREFIX = "kakao"

        private val NONADMIN_STUDENT_ROLES =
            SofiaActor.NONADMIN_STUDENT.permissions.map { SimpleGrantedAuthority(it.springRoleName) }
        private val SOFIA_ADMIN_ROLES =
            SofiaActor.ADMIN_STUDENT.permissions.map { SimpleGrantedAuthority(it.springRoleName) }
        private val SOFIA_KAKAO_ROLES =
            SofiaActor.KAKAO_SKILL_SERVER.permissions.map { SimpleGrantedAuthority(it.springRoleName) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        request.getHeader("Authorization")?.let { header ->
            when {
                header.startsWith(USER_PREFIX) -> authenticateUser(header.removePrefix(USER_PREFIX).trim())
                header.startsWith(KAKAO_PREFIX) -> authenticateKakao(header.removePrefix(KAKAO_PREFIX).trim())
                else -> throw BadCredentialsException("지원되지 않는 인증 유형입니다.")
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun authenticateUser(token: String) {
        val uuidToken = try {
            UUID.fromString(token)
        } catch (ex: IllegalArgumentException) {
            throw BadCredentialsException("사용자 인증방식에서 토큰이 UUID 형식으로 주어지지 않았습니다.", ex)
        }

        val userAuth = userAuthRepository.findBySecretToken(uuidToken).orElseGet {
            throw BadCredentialsException("사용자를 찾을 수 없습니다.")
        }

        val user = userAuth.user
        log.info { "사용자가 인증되었습니다. 접근인=${user.studentName}" }
        SecurityContextHolder.getContext().authentication = ApiKeyAuthenticationToken(
            principal = user,
            authorities = userAuth.role.toSpringRoles(),
        )
    }

    private fun SofiaUserRole.toSpringRoles(): List<SimpleGrantedAuthority> = when (this) {
        SofiaUserRole.STUDENT -> NONADMIN_STUDENT_ROLES
        SofiaUserRole.ADMIN -> SOFIA_ADMIN_ROLES
    }

    private fun authenticateKakao(token: String) {
        if (token != kakaoSkillSecretProperties.secretToken) {
            throw BadCredentialsException("스킬 서버를 인증할 수 없습니다.")
        }

        log.info { "카카오 스킬 서버가 인증되었습니다." }
        SecurityContextHolder.getContext().authentication = ApiKeyAuthenticationToken(
            principal = KakaoPrincipal(),
            authorities = SOFIA_KAKAO_ROLES,
        )
    }
}
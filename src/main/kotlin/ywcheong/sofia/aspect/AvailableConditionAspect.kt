package ywcheong.sofia.aspect

import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import ywcheong.sofia.phase.SystemPhaseService
import ywcheong.sofia.user.auth.SofiaPermission

@Aspect
@Component
class AvailableConditionAspect(
    private val systemPhaseService: SystemPhaseService,
) {
    private val logger = KotlinLogging.logger {}

    // @Transactional // @Aspect가 붙어 있기 때문에 @Transactional AOP가 작동하지 않음 -> systemPhaseService의 @Transaction으로 대체
    @Around("@annotation(availableCondition)")
    fun checkPhase(joinPoint: ProceedingJoinPoint, availableCondition: AvailableCondition): Any? {
        logger.debug { "Checking phase for ${joinPoint.signature}: required ${availableCondition.phases.toList()}" }
        checkPermissionsOrThrow(availableCondition.permissions)
        return systemPhaseService.executeIfPhase(availableCondition.phases.toList()) {
            joinPoint.proceed()
        }
    }

    private fun checkPermissionsOrThrow(requiredPermissions: Array<SofiaPermission>) {
        if (requiredPermissions.isEmpty()) return // 필요한 권한이 없으면 당연승인

        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("인증 정보가 없습니다.")

        val grantedRoles = authentication.authorities.map { it.authority }.toSet()
        val hasRequiredRole = requiredPermissions.any { it.springRoleName in grantedRoles }

        if (!hasRequiredRole) {
            logger.info { "보유 권한 중 필요 권한에 해당하는 것이 아무것도 없습니다. 보유 권한 (ALL): [${grantedRoles.joinToString(", ")}] / 필요 권한 (OR): [${requiredPermissions.joinToString(", ") { it.springRoleName }}]" }
            throw AccessDeniedException("접근 권한이 없습니다.")
        }
    }
}

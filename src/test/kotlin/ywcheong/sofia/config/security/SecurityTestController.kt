package ywcheong.sofia.config.security

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.auth.SofiaPermission

/**
 * 보안 기능 테스트를 위한 테스트 전용 컨트롤러.
 * 실제 비즈니스 로직 없이 인증/인가 동작만 검증한다.
 */
@RestController
@RequestMapping("/test/security")
class SecurityTestController {

    @AvailableCondition(
        phases = [SystemPhase.DEACTIVATION, SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = []
    )
    @GetMapping("/public")
    fun publicEndpoint(): Map<String, String> = mapOf("result" to "public")

    @AvailableCondition(
        phases = [SystemPhase.DEACTIVATION, SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.STUDENT_LEVEL]
    )
    @GetMapping("/user")
    fun userEndpoint(@CurrentUser user: SofiaUser): Map<String, String> =
        mapOf("result" to "user", "studentNumber" to user.studentNumber)

    @AvailableCondition(
        phases = [SystemPhase.DEACTIVATION, SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @GetMapping("/admin")
    fun adminEndpoint(): Map<String, String> = mapOf("result" to "admin")

    @AvailableCondition(
        phases = [SystemPhase.DEACTIVATION, SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.KAKAO_ENDPOINT]
    )
    @GetMapping("/kakao")
    fun kakaoEndpoint(): Map<String, String> = mapOf("result" to "kakao")
}

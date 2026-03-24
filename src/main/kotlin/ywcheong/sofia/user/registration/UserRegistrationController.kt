package ywcheong.sofia.user.registration

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.auth.SofiaPermission
import java.util.*

@RestController
@RequestMapping("/user/registrations")
@Tag(name = "User Registration", description = "회원가입 신청 목록 조회 및 승인/거부 API")
class UserRegistrationController(
    private val userRegistrationService: UserRegistrationService,
) {
    private val logger = KotlinLogging.logger {}

    // 신청 목록 조회
    data class RegistrationSummaryResponse(
        val id: UUID,
        val studentNumber: String,
        val studentName: String,
    )

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @GetMapping
    fun findAllRegistrations(pageable: Pageable): Page<RegistrationSummaryResponse> {
        logger.info { "신청 목록 조회 요청: page=${pageable.pageNumber}, size=${pageable.pageSize}" }
        return userRegistrationService.findAllRegistrations(pageable)
    }

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping("/{id}/acceptance")
    fun acceptRegistration(@PathVariable id: UUID) {
        logger.info { "회원가입 승인 요청: registrationId=$id" }
        userRegistrationService.acceptRegistration(id)
    }

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping("/{id}/rejection")
    fun denyRegistration(@PathVariable id: UUID) {
        logger.info { "회원가입 거부 요청: registrationId=$id" }
        userRegistrationService.denyRegistration(id)
    }
}
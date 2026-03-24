package ywcheong.sofia.phase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.user.auth.SofiaPermission

@RestController
@RequestMapping("/system-phase")
@Tag(name = "System Phase", description = "시스템 운영 페이즈 조회 및 전환 API")
class SystemPhaseController(
    private val systemPhaseService: SystemPhaseService,
) {
    private val logger = KotlinLogging.logger {}

    data class GetCurrentPhaseResponse(
        val currentPhase: SystemPhase,
        val currentPhaseDisplayName: String,
        val nextPhase: SystemPhase,
        val nextPhaseDisplayName: String,
    )

    // UC-012: 현재 페이즈 조회
    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping
    fun getCurrentPhase(): GetCurrentPhaseResponse {
        val currentPhase = systemPhaseService.getCurrentPhase()
        return GetCurrentPhaseResponse(
            currentPhase = currentPhase,
            currentPhaseDisplayName = currentPhase.displayName,
            nextPhase = currentPhase.nextPhase,
            nextPhaseDisplayName = currentPhase.nextPhase.displayName,
        )
    }

    data class TransitPhaseRequest(
        val nextPhase: SystemPhase,
    )

    data class TransitPhaseResponse(
        val previousPhase: SystemPhase,
        val currentPhase: SystemPhase,
    )

    // UC-012: 페이즈 전환
    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PostMapping("/transit")
    fun transitPhase(@RequestBody request: TransitPhaseRequest): TransitPhaseResponse {
        logger.info { "페이즈 전환 요청: nextPhase=${request.nextPhase}" }

        val previousPhase = systemPhaseService.getCurrentPhase()
        systemPhaseService.transitPhase(request.nextPhase)

        logger.info { "페이즈 전환 완료: $previousPhase -> ${request.nextPhase}" }

        return TransitPhaseResponse(
            previousPhase = previousPhase,
            currentPhase = request.nextPhase,
        )
    }
}

package ywcheong.sofia.phase

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.config.security.CurrentUser
import ywcheong.sofia.user.SofiaUser
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
    @Operation(summary = "현재 시스템 페이즈 조회", description = "현재 페이즈와 다음 페이즈 정보를 반환")
    fun getCurrentPhase(): GetCurrentPhaseResponse {
        val currentPhase = systemPhaseService.getCurrentPhase()
        return GetCurrentPhaseResponse(
            currentPhase = currentPhase,
            currentPhaseDisplayName = currentPhase.displayName,
            nextPhase = currentPhase.nextPhase,
            nextPhaseDisplayName = currentPhase.nextPhase.displayName,
        )
    }

    // === RECRUITMENT (DEACTIVATION → RECRUITMENT) ===

    data class TransitAvailabilityResponse(
        val available: Boolean,
        val pendingRegistrations: List<PendingRegistrationResponse> = emptyList(),
        val incompleteTasks: List<IncompleteTaskResponse> = emptyList(),
    )

    data class PendingRegistrationResponse(
        val id: java.util.UUID,
        val studentNumber: String,
        val studentName: String,
    )

    data class IncompleteTaskResponse(
        val id: java.util.UUID,
        val taskType: String,
        val description: String,
        val assigneeName: String,
    )

    data class DeactivationAvailabilityResponse(
        val available: Boolean,
    )

    data class TransitDeactivationRequest(
        val userRetentionMode: SystemPhaseService.UserRetentionMode,
    )

    data class TransitResponse(
        val currentPhase: SystemPhase,
        val currentPhaseDisplayName: String,
    )

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping("/transit/recruitment/availability")
    fun checkRecruitmentAvailability(): TransitAvailabilityResponse {
        val result = systemPhaseService.checkRecruitmentAvailability()
        return TransitAvailabilityResponse(available = result.available)
    }

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PostMapping("/transit/recruitment")
    fun transitToRecruitment(): TransitResponse {
        logger.info { "페이즈 전환 요청: DEACTIVATION -> RECRUITMENT" }
        systemPhaseService.transitToRecruitment()
        logger.info { "페이즈 전환 완료: RECRUITMENT" }
        return TransitResponse(
            currentPhase = SystemPhase.RECRUITMENT,
            currentPhaseDisplayName = SystemPhase.RECRUITMENT.displayName,
        )
    }

    // === TRANSLATION (RECRUITMENT → TRANSLATION) ===

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping("/transit/translation/availability")
    @Operation(summary = "TRANSLATION 전환 가능 여부 확인", description = "미처리 회원가입 신청이 있으면 available=false, 해당 목록 반환")
    fun checkTranslationAvailability(): TransitAvailabilityResponse {
        val result = systemPhaseService.checkTranslationAvailability()
        return TransitAvailabilityResponse(
            available = result.available,
            pendingRegistrations = result.pendingRegistrations.map {
                PendingRegistrationResponse(
                    id = it.id,
                    studentNumber = it.studentNumber,
                    studentName = it.studentName,
                )
            },
        )
    }

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PostMapping("/transit/translation")
    fun transitToTranslation(): TransitResponse {
        logger.info { "페이즈 전환 요청: RECRUITMENT -> TRANSLATION" }
        systemPhaseService.transitToTranslation()
        logger.info { "페이즈 전환 완료: TRANSLATION" }
        return TransitResponse(
            currentPhase = SystemPhase.TRANSLATION,
            currentPhaseDisplayName = SystemPhase.TRANSLATION.displayName,
        )
    }

    // === SETTLEMENT (TRANSLATION → SETTLEMENT) ===

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping("/transit/settlement/availability")
    @Operation(summary = "SETTLEMENT 전환 가능 여부 확인", description = "미처리 신청 또는 미완료 과제가 있으면 available=false, 해당 목록 반환")
    fun checkSettlementAvailability(): TransitAvailabilityResponse {
        val result = systemPhaseService.checkSettlementAvailability()
        return TransitAvailabilityResponse(
            available = result.available,
            pendingRegistrations = result.pendingRegistrations.map {
                PendingRegistrationResponse(
                    id = it.id,
                    studentNumber = it.studentNumber,
                    studentName = it.studentName,
                )
            },
            incompleteTasks = result.incompleteTasks.map {
                IncompleteTaskResponse(
                    id = it.id,
                    taskType = it.taskType,
                    description = it.description,
                    assigneeName = it.assigneeName,
                )
            },
        )
    }

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PostMapping("/transit/settlement")
    fun transitToSettlement(): TransitResponse {
        logger.info { "페이즈 전환 요청: TRANSLATION -> SETTLEMENT" }
        systemPhaseService.transitToSettlement()
        logger.info { "페이즈 전환 완료: SETTLEMENT" }
        return TransitResponse(
            currentPhase = SystemPhase.SETTLEMENT,
            currentPhaseDisplayName = SystemPhase.SETTLEMENT.displayName,
        )
    }

    // === DEACTIVATION (SETTLEMENT → DEACTIVATION) ===

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @GetMapping("/transit/deactivation/availability")
    fun checkDeactivationAvailability(): DeactivationAvailabilityResponse {
        val result = systemPhaseService.checkDeactivationAvailability()
        return DeactivationAvailabilityResponse(
            available = result.available,
        )
    }

    @AvailableCondition(phases = [], permissions = [SofiaPermission.ADMIN_LEVEL])
    @PostMapping("/transit/deactivation")
    @Operation(summary = "DEACTIVATION으로 전환", description = "userRetentionMode로 기존 사용자 데이터 유지/삭제 선택")
    fun transitToDeactivation(
        @RequestBody request: TransitDeactivationRequest,
        @CurrentUser user: SofiaUser,
    ): TransitResponse {
        logger.info { "페이즈 전환 요청: SETTLEMENT -> DEACTIVATION (mode=${request.userRetentionMode})" }
        systemPhaseService.transitToDeactivation(request.userRetentionMode, user.id)
        logger.info { "페이즈 전환 완료: DEACTIVATION" }
        return TransitResponse(
            currentPhase = SystemPhase.DEACTIVATION,
            currentPhaseDisplayName = SystemPhase.DEACTIVATION.displayName,
        )
    }
}

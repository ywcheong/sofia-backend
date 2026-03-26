package ywcheong.sofia.user

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.commons.PageResponse
import ywcheong.sofia.commons.SortDirection
import ywcheong.sofia.commons.SortRequest
import ywcheong.sofia.config.security.CurrentUser
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.user.auth.SofiaPermission
import java.util.*

@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "사용자 목록 조회, 휴식 설정, 보정 자수 조정, 관리자 승급/강등 API")
class UserManagementController(
    private val userManagementService: UserManagementService,
) {
    private val logger = KotlinLogging.logger {}

    // 사용자 목록 조회
    data class UserSummaryResponse(
        val id: UUID,
        val studentNumber: String,
        val studentName: String,
        val role: SofiaUserRole,
        val rest: Boolean,
        val warningCount: Int,
        val completedCharCount: Int,
        val adjustedCharCount: Int,
        val totalCharCount: Int,
    )

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @GetMapping
    @Operation(
        summary = "사용자 목록 조회",
        description = "search는 학번+이름 부분검색, role/rest는 선택적 필터. " +
            "sortField: id, studentNumber, studentName, role, rest, warningCount, totalCharCount 중 하나. " +
            "sortDirection: ASC 또는 DESC"
    )
    fun findAllUsers(
        pageable: Pageable,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) role: SofiaUserRole?,
        @RequestParam(required = false) rest: Boolean?,
        @RequestParam(required = false) sortField: String?,
        @RequestParam(required = false) sortDirection: SortDirection?,
    ): PageResponse<UserSummaryResponse> {
        logger.info {
            "사용자 목록 조회 요청: page=${pageable.pageNumber}, size=${pageable.pageSize}, " +
                "search=$search, role=$role, rest=$rest, sortField=$sortField, sortDirection=$sortDirection"
        }

        val condition = UserManagementService.FindAllUsersCondition(
            search = search,
            role = role,
            rest = rest,
        )

        val sortRequest = SortRequest(sortField, sortDirection)
        val resultPage = userManagementService.findAllUsers(condition, sortRequest, pageable)

        return PageResponse.from(resultPage.map { result ->
            UserSummaryResponse(
                id = result.id,
                studentNumber = result.studentNumber,
                studentName = result.studentName,
                role = result.role,
                rest = result.rest,
                warningCount = result.warningCount,
                completedCharCount = result.completedCharCount,
                adjustedCharCount = result.adjustedCharCount,
                totalCharCount = result.totalCharCount,
            )
        })
    }

    // UC-013: 개인 휴식 설정
    data class SetRestStatusRequest(
        val rest: Boolean,
    )

    data class SetRestStatusResponse(
        val userId: UUID,
        val rest: Boolean,
    )

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping("/{userId}/rest")
    @Operation(summary = "사용자 휴식 상태 설정", description = "휴식 중인 사용자는 과제 자동배정에서 제외됨")
    fun setRestStatus(
        @PathVariable userId: UUID,
        @RequestBody request: SetRestStatusRequest,
    ): SetRestStatusResponse {
        logger.info { "사용자 휴식 상태 설정 요청: userId=$userId, isResting=${request.rest}" }

        val command = UserManagementService.SetRestStatusCommand(
            userId = userId,
            rest = request.rest,
        )

        val user = userManagementService.setRestStatus(command)

        return SetRestStatusResponse(
            userId = user.id,
            rest = request.rest,
        )
    }

    // 보정 자수 부여/차감
    data class AdjustCharCountRequest(
        val amount: Int,
    )

    data class AdjustCharCountResponse(
        val userId: UUID,
        val amount: Int,
        val adjustedCharCount: Int,
    )

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping("/{userId}/adjust-char-count")
    @Operation(summary = "보정 자수 조정", description = "amount 양수면 부여, 음수면 차감")
    fun adjustCharCount(
        @PathVariable userId: UUID,
        @RequestBody request: AdjustCharCountRequest,
    ): AdjustCharCountResponse {
        logger.info { "보정 자수 조정 요청: userId=$userId, amount=${request.amount}" }

        val command = UserManagementService.AdjustCharCountCommand(
            userId = userId,
            amount = request.amount,
        )

        val user = userManagementService.adjustCharCount(command)

        return AdjustCharCountResponse(
            userId = user.id,
            amount = request.amount,
            adjustedCharCount = user.taskStatus.adjustedCharCount,
        )
    }

    // 경고 수 부여/차감
    data class AdjustWarningCountRequest(
        val amount: Int,
    )

    data class AdjustWarningCountResponse(
        val userId: UUID,
        val amount: Int,
        val warningCount: Int,
    )

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping("/{userId}/adjust-warning-count")
    @Operation(summary = "경고 수 조정", description = "amount 양수면 부여, 음수면 차감")
    fun adjustWarningCount(
        @PathVariable userId: UUID,
        @RequestBody request: AdjustWarningCountRequest,
    ): AdjustWarningCountResponse {
        logger.info { "경고 수 조정 요청: userId=$userId, amount=${request.amount}" }

        val command = UserManagementService.AdjustWarningCountCommand(
            userId = userId,
            amount = request.amount,
        )

        val user = userManagementService.adjustWarningCount(command)

        return AdjustWarningCountResponse(
            userId = user.id,
            amount = request.amount,
            warningCount = user.taskStatus.warningCount,
        )
    }

    // UC-014: 관리자 승급
    data class PromoteToAdminResponse(
        val userId: UUID,
        val role: SofiaUserRole,
    )

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping("/{userId}/promote")
    fun promoteToAdmin(
        @PathVariable userId: UUID,
    ): PromoteToAdminResponse {
        logger.info { "관리자 승급 요청: userId=$userId" }

        val command = UserManagementService.PromoteToAdminCommand(userId = userId)
        val user = userManagementService.promoteToAdmin(command)

        return PromoteToAdminResponse(
            userId = user.id,
            role = user.auth.role,
        )
    }

    // UC-015: 관리자 강등
    data class DemoteFromAdminResponse(
        val userId: UUID,
        val role: SofiaUserRole,
    )

    @AvailableCondition(
        phases = [SystemPhase.RECRUITMENT, SystemPhase.TRANSLATION, SystemPhase.SETTLEMENT],
        permissions = [SofiaPermission.ADMIN_LEVEL]
    )
    @PostMapping("/{userId}/demote")
    @Operation(summary = "관리자 강등", description = "자기 자신은 강등할 수 없음")
    fun demoteFromAdmin(
        @PathVariable userId: UUID,
        @CurrentUser currentUser: SofiaUser,
    ): DemoteFromAdminResponse {
        logger.info { "관리자 강등 요청: userId=$userId" }

        val command = UserManagementService.DemoteFromAdminCommand(
            userId = userId,
            currentUserId = currentUser.id,
        )
        val user = userManagementService.demoteFromAdmin(command)

        return DemoteFromAdminResponse(
            userId = user.id,
            role = user.auth.role,
        )
    }
}

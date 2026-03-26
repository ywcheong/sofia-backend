package ywcheong.sofia.user

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.commons.SortRequest
import ywcheong.sofia.email.EmailSendService
import ywcheong.sofia.email.templates.DemotedFromAdminEmailTemplate
import ywcheong.sofia.email.templates.PromotedToAdminEmailTemplate
import ywcheong.sofia.task.user.SofiaUserTaskStatusRepository
import ywcheong.sofia.user.auth.SofiaUserAuthRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class UserManagementService(
    private val sofiaUserRepository: SofiaUserRepository,
    private val sofiaUserTaskStatusRepository: SofiaUserTaskStatusRepository,
    private val sofiaUserAuthRepository: SofiaUserAuthRepository,
    private val emailSendService: EmailSendService,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        val ALLOWED_SORT_FIELDS = setOf(
            "id", "studentNumber", "studentName", "role", "rest", "warningCount", "totalCharCount"
        )

        private fun convertBytesToUUID(bytes: ByteArray): UUID {
            checkNotNull(bytes) { "UUID bytes cannot be null" }
            val byteBuffer = java.nio.ByteBuffer.wrap(bytes)
            val mostSignificantBits = byteBuffer.long
            val leastSignificantBits = byteBuffer.long
            return UUID(mostSignificantBits, leastSignificantBits)
        }
    }

    /**
     * 사용자 목록 조회 결과
     */
    data class FindAllUsersResult(
        val id: UUID,
        val studentNumber: String,
        val studentName: String,
        val role: SofiaUserRole,
        val rest: Boolean,
        val warningCount: Int,
        val adjustedCharCount: Int,
        val completedCharCount: Int,
        val totalCharCount: Int,
    )

    /**
     * 사용자 목록 조회 조건
     */
    data class FindAllUsersCondition(
        val search: String?,
        val role: SofiaUserRole?,
        val rest: Boolean?,
    )

    fun findAllUsers(
        condition: FindAllUsersCondition,
        sortRequest: SortRequest,
        pageable: Pageable
    ): Page<FindAllUsersResult> {
        sortRequest.validate()

        // 정렬 필드 검증
        val sortField = sortRequest.sortField
        if (sortField != null && sortField !in ALLOWED_SORT_FIELDS) {
            throw BusinessException("정렬할 수 없는 필드입니다: $sortField. 허용된 필드: ${ALLOWED_SORT_FIELDS.joinToString()}")
        }

        // Repository 호출
        val projectionPage = sofiaUserRepository.findAllUsersForManagement(
            search = condition.search,
            role = condition.role?.name,
            rest = condition.rest,
            sortField = sortField,
            sortDir = sortRequest.sortDirection?.name,
            pageable = pageable
        )

        // Projection을 Result로 변환
        return projectionPage.map { proj ->
            FindAllUsersResult(
                id = convertBytesToUUID(proj.getId()),
                studentNumber = proj.getStudentNumber(),
                studentName = proj.getStudentName(),
                role = SofiaUserRole.valueOf(proj.getRole()),
                rest = proj.getRest(),
                warningCount = proj.getWarningCount(),
                adjustedCharCount = proj.getAdjustedCharCount(),
                completedCharCount = proj.getCompletedCharCount(),
                totalCharCount = proj.getTotalCharCount(),
            )
        }
    }

    data class SetRestStatusCommand(
        val userId: UUID,
        val rest: Boolean,
    )

    data class AdjustCharCountCommand(
        val userId: UUID,
        val amount: Int,
    ) {
        init {
            if (amount == 0) {
                throw BusinessException("보정 자수는 0이 될 수 없습니다.")
            }
        }
    }

    @Transactional
    fun setRestStatus(command: SetRestStatusCommand): SofiaUser {
        val userTask = sofiaUserTaskStatusRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        // 이미 동일한 상태인 경우
        if (userTask.rest == command.rest) {
            return userTask.user
        }

        // 휴식 상태로 변경 시, 마지막 활성 사용자인지 확인
        if (command.rest) {
            val activeCount = sofiaUserTaskStatusRepository.countByRest(false)
            if (activeCount <= 1) {
                throw BusinessException("모든 번역버디가 휴식 상태가 되어 과제를 할당할 수 없습니다.")
            }
        }

        userTask.updateRestStatus(command.rest)
        logger.info { "사용자 휴식 상태 변경: userId=${command.userId}, isResting=${command.rest}" }

        return userTask.user
    }

    @Transactional
    fun adjustCharCount(command: AdjustCharCountCommand): SofiaUser {
        val userTask = sofiaUserTaskStatusRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        userTask.adjustCharCount(command.amount)

        val action = if (command.amount > 0) "부여" else "차감"
        logger.info { "보정 자수 $action: userId=${command.userId}, amount=${command.amount}, newAdjustedCharCount=${userTask.adjustedCharCount}" }

        return userTask.user
    }

    // UC-014: 관리자 승급
    data class PromoteToAdminCommand(
        val userId: UUID,
    )

    // UC-015: 관리자 강등
    data class DemoteFromAdminCommand(
        val userId: UUID,
        val currentUserId: UUID,
    )

    @Transactional
    fun promoteToAdmin(command: PromoteToAdminCommand): SofiaUser {
        val userAuth = sofiaUserAuthRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        if (userAuth.role == SofiaUserRole.ADMIN) {
            throw BusinessException("이미 관리자 권한을 가진 사용자입니다.")
        }

        userAuth.updateRole(SofiaUserRole.ADMIN)
        logger.info { "관리자 승급: userId=${command.userId}" }

        // US-029: 관리자 승급 이메일 발송
        sendPromotedToAdminEmail(userAuth.user)

        return userAuth.user
    }

    @Transactional
    fun demoteFromAdmin(command: DemoteFromAdminCommand): SofiaUser {
        // 자기 자신을 강등할 수 없음
        if (command.userId == command.currentUserId) {
            throw BusinessException("자기 자신을 강등할 수 없습니다.")
        }

        val userAuth = sofiaUserAuthRepository.findByIdOrNull(command.userId)
            ?: throw BusinessException("존재하지 않는 사용자입니다.")

        if (userAuth.role == SofiaUserRole.STUDENT) {
            throw BusinessException("관리자 권한이 없는 사용자입니다.")
        }

        // 마지막 관리자인지 확인
        val adminCount = sofiaUserAuthRepository.countByRole(SofiaUserRole.ADMIN)
        if (adminCount <= 1) {
            throw BusinessException("마지막 관리자는 강등할 수 없습니다.")
        }

        userAuth.updateRole(SofiaUserRole.STUDENT)
        logger.info { "관리자 강등: userId=${command.userId}" }

        // US-029: 관리자 강등 이메일 발송
        sendDemotedFromAdminEmail(userAuth.user)

        return userAuth.user
    }

    private fun sendPromotedToAdminEmail(user: SofiaUser) {
        val template = PromotedToAdminEmailTemplate(
            recipientEmail = user.email.email,
            recipientName = user.studentName,
            unsubscribeToken = user.email.unsubscribeToken.toString(),
            promotedAt = formatDateTime(Instant.now()),
        )
        emailSendService.sendEmail(template)
    }

    private fun sendDemotedFromAdminEmail(user: SofiaUser) {
        val template = DemotedFromAdminEmailTemplate(
            recipientEmail = user.email.email,
            recipientName = user.studentName,
            unsubscribeToken = user.email.unsubscribeToken.toString(),
            demotionReason = "관리자 권한이 해제되었습니다.",
            demotedAt = formatDateTime(Instant.now()),
        )
        emailSendService.sendEmail(template)
    }

    private fun formatDateTime(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"))
        return formatter.format(instant)
    }
}

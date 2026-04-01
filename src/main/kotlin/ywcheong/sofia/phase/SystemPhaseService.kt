package ywcheong.sofia.phase

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.task.TranslationTaskRepository
import ywcheong.sofia.user.SofiaUserRepository
import ywcheong.sofia.user.SofiaUserRole
import ywcheong.sofia.user.registration.UserRegistrationRepository
import java.util.*


@Service
class SystemPhaseService(
    private val systemPhaseRepository: SystemPhaseRepository,
    private val userRegistrationRepository: UserRegistrationRepository,
    private val translationTaskRepository: TranslationTaskRepository,
    private val sofiaUserRepository: SofiaUserRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun getCurrentPhase(): SystemPhase {
        val entity = systemPhaseRepository.findById(SystemPhaseEntity.PHASE_ENTITY_ID)
            .orElseThrow { IllegalStateException("SystemPhaseEntity not found") }
        return entity.currentPhase
    }

    @Transactional
    fun <T> executeIfPhase(phases: List<SystemPhase>, action: () -> T): T {
        val entity = systemPhaseRepository.findWithReadLockById(SystemPhaseEntity.PHASE_ENTITY_ID)
            .orElseThrow { IllegalStateException("SystemPhaseEntity not found") }

        if (phases.isNotEmpty() && entity.currentPhase !in phases) {
            logger.warn { "Phase mismatch: current=${entity.currentPhase}, expected=$phases" }
            throw BusinessException("현재 ${entity.currentPhase.displayName} 단계입니다. 해당 기능은 ${phases.joinToString(", ") { it.displayName }} 단계에서만 사용할 수 있습니다.")
        }

        logger.debug { "Executing action in phase: ${entity.currentPhase}" }
        return action()
    }

    // === RECRUITMENT (DEACTIVATION → RECRUITMENT) ===

    data class TransitAvailabilityResult(
        val available: Boolean,
        val pendingRegistrations: List<PendingRegistrationInfo> = emptyList(),
        val incompleteTasks: List<IncompleteTaskInfo> = emptyList(),
    ) {
        data class PendingRegistrationInfo(
            val id: UUID,
            val studentNumber: String,
            val studentName: String,
        )

        data class IncompleteTaskInfo(
            val id: UUID,
            val taskType: String,
            val description: String,
            val assigneeName: String,
        )
    }

    fun checkRecruitmentAvailability(): TransitAvailabilityResult {
        // 조건 없음
        return TransitAvailabilityResult(available = true)
    }

    @Transactional
    fun transitToRecruitment() {
        val entity = systemPhaseRepository.findWithWriteLockById(SystemPhaseEntity.PHASE_ENTITY_ID)
            .orElseThrow { IllegalStateException("SystemPhaseEntity not found") }

        validateCurrentPhase(entity.currentPhase, SystemPhase.DEACTIVATION)

        logger.info { "Phase transition: ${entity.currentPhase} -> RECRUITMENT" }
        entity.currentPhase = SystemPhase.RECRUITMENT
    }

    // === TRANSLATION (RECRUITMENT → TRANSLATION) ===

    fun checkTranslationAvailability(): TransitAvailabilityResult {
        val pendingRegistrations = userRegistrationRepository.findAll()

        return if (pendingRegistrations.isEmpty()) {
            TransitAvailabilityResult(available = true)
        } else {
            TransitAvailabilityResult(
                available = false,
                pendingRegistrations = pendingRegistrations.map {
                    TransitAvailabilityResult.PendingRegistrationInfo(
                        id = it.id,
                        studentNumber = it.studentNumber,
                        studentName = it.studentName,
                    )
                }
            )
        }
    }

    @Transactional
    fun transitToTranslation() {
        val entity = systemPhaseRepository.findWithWriteLockById(SystemPhaseEntity.PHASE_ENTITY_ID)
            .orElseThrow { IllegalStateException("SystemPhaseEntity not found") }

        validateCurrentPhase(entity.currentPhase, SystemPhase.RECRUITMENT)

        val pendingRegistrations = userRegistrationRepository.findAll()
        if (pendingRegistrations.isNotEmpty()) {
            throw BusinessException("대기 중인 참가 신청이 ${pendingRegistrations.size}건 있습니다. 모두 승인하거나 거부한 후 전환할 수 있습니다.")
        }

        logger.info { "Phase transition: ${entity.currentPhase} -> TRANSLATION" }
        entity.currentPhase = SystemPhase.TRANSLATION
    }

    // === SETTLEMENT (TRANSLATION → SETTLEMENT) ===

    fun checkSettlementAvailability(): TransitAvailabilityResult {
        val pendingRegistrations = userRegistrationRepository.findAll()
        val incompleteTasks = translationTaskRepository.findAllIncompleteTasks()

        return if (pendingRegistrations.isEmpty() && incompleteTasks.isEmpty()) {
            TransitAvailabilityResult(available = true)
        } else {
            TransitAvailabilityResult(
                available = false,
                pendingRegistrations = pendingRegistrations.map {
                    TransitAvailabilityResult.PendingRegistrationInfo(
                        id = it.id,
                        studentNumber = it.studentNumber,
                        studentName = it.studentName,
                    )
                },
                incompleteTasks = incompleteTasks.map {
                    TransitAvailabilityResult.IncompleteTaskInfo(
                        id = it.id,
                        taskType = it.taskType.name,
                        description = it.taskDescription,
                        assigneeName = it.assignee.studentName,
                    )
                }
            )
        }
    }

    @Transactional
    fun transitToSettlement() {
        val entity = systemPhaseRepository.findWithWriteLockById(SystemPhaseEntity.PHASE_ENTITY_ID)
            .orElseThrow { IllegalStateException("SystemPhaseEntity not found") }

        validateCurrentPhase(entity.currentPhase, SystemPhase.TRANSLATION)

        val pendingRegistrations = userRegistrationRepository.findAll()
        if (pendingRegistrations.isNotEmpty()) {
            throw BusinessException("대기 중인 참가 신청이 ${pendingRegistrations.size}건 있습니다. 모두 승인하거나 거부한 후 전환할 수 있습니다.")
        }

        val incompleteTasks = translationTaskRepository.findAllIncompleteTasks()
        if (incompleteTasks.isNotEmpty()) {
            throw BusinessException("미완료된 번역 과제가 ${incompleteTasks.size}건 있습니다. 모든 과제가 완료되어야 합니다.")
        }

        logger.info { "Phase transition: ${entity.currentPhase} -> SETTLEMENT" }
        entity.currentPhase = SystemPhase.SETTLEMENT
    }

    // === DEACTIVATION (SETTLEMENT → DEACTIVATION) ===

    data class DeactivationAvailabilityResult(
        val available: Boolean,
    )

    enum class UserRetentionMode {
        KEEP_ALL,
        KEEP_ADMINS,
        KEEP_SELF,
    }

    fun checkDeactivationAvailability(): DeactivationAvailabilityResult {
        // 조건 없음, 옵션만 반환
        return DeactivationAvailabilityResult(
            available = true,
        )
    }

    @Transactional
    fun transitToDeactivation(mode: UserRetentionMode, currentUserId: UUID) {
        val entity = systemPhaseRepository.findWithWriteLockById(SystemPhaseEntity.PHASE_ENTITY_ID)
            .orElseThrow { IllegalStateException("SystemPhaseEntity not found") }

        validateCurrentPhase(entity.currentPhase, SystemPhase.SETTLEMENT)

        // 모든 번역 과제 삭제 (FK: TranslationTask -> SofiaUser 이므로 사용자 삭제 전에 삭제)
        logger.info { "Deleting all translation tasks during deactivation" }
        translationTaskRepository.deleteAllInBatch()

        // 사용자 정리 로직
        when (mode) {
            UserRetentionMode.KEEP_ALL -> {
                logger.info { "Keeping all users during deactivation" }
            }

            UserRetentionMode.KEEP_ADMINS -> {
                logger.info { "Deleting all students during deactivation" }
                sofiaUserRepository.deleteByAuthRole(SofiaUserRole.STUDENT)
            }

            UserRetentionMode.KEEP_SELF -> {
                logger.info { "Keeping only current user $currentUserId during deactivation" }
                sofiaUserRepository.findAll().forEach { user ->
                    if (user.id != currentUserId) {
                        sofiaUserRepository.delete(user)
                    }
                }
            }
        }

        logger.info { "Phase transition: ${entity.currentPhase} -> DEACTIVATION (mode=$mode)" }
        entity.currentPhase = SystemPhase.DEACTIVATION
    }

    // === Helper methods ===

    private fun validateCurrentPhase(current: SystemPhase, expected: SystemPhase) {
        if (current != expected) {
            throw BusinessException("현재 ${current.displayName} 단계입니다. ${expected.displayName} 단계에서만 이 전환을 수행할 수 있습니다.")
        }
    }
}

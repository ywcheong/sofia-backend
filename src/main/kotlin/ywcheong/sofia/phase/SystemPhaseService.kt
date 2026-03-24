package ywcheong.sofia.phase

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import ywcheong.sofia.commons.BusinessException


@Service
class SystemPhaseService(
    private val systemPhaseRepository: SystemPhaseRepository,
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

    @Transactional
    fun transitPhase(nextPhase: SystemPhase) {
        val entity = systemPhaseRepository.findWithWriteLockById(SystemPhaseEntity.PHASE_ENTITY_ID)
            .orElseThrow { IllegalStateException("SystemPhaseEntity not found") }

        if (entity.currentPhase.nextPhase != nextPhase) {
            logger.warn { "Invalid phase transition: ${entity.currentPhase} -> $nextPhase (expected: ${entity.currentPhase.nextPhase})" }
            throw BusinessException("현재 ${entity.currentPhase.displayName} 단계입니다. 다음 단계인 ${entity.currentPhase.nextPhase.displayName}(으)로만 전환할 수 있습니다.")
        }

        logger.info { "Phase transition: ${entity.currentPhase} -> $nextPhase" }
        entity.currentPhase = nextPhase
    }
}

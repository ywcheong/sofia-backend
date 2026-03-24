package ywcheong.sofia.phase

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class SystemPhaseInitializer(
    private val systemPhaseRepository: SystemPhaseRepository,
) : CommandLineRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        if (!systemPhaseRepository.existsById(SystemPhaseEntity.PHASE_ENTITY_ID)) {
            logger.info { "Initializing SystemPhaseEntity with default phase: ${SystemPhase.DEACTIVATION}" }
            systemPhaseRepository.save(
                SystemPhaseEntity(
                    currentPhase = SystemPhase.DEACTIVATION,
                ),
            )
        }
    }
}

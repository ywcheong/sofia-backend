package ywcheong.sofia.phase

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id

/** 단일 행 - Id는 항상 1 */
@Entity
class SystemPhaseEntity (
    @Id
    private val id: Int = PHASE_ENTITY_ID,
    @Enumerated(EnumType.STRING)
    var currentPhase: SystemPhase,
) {
    companion object {
        const val PHASE_ENTITY_ID = 1
    }
}
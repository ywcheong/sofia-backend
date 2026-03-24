package ywcheong.sofia.phase

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface SystemPhaseRepository: JpaRepository<SystemPhaseEntity, Int> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT e FROM SystemPhaseEntity e WHERE e.id = :id")
    fun findWithReadLockById(@Param("id") id: Int): Optional<SystemPhaseEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM SystemPhaseEntity e WHERE e.id = :id")
    fun findWithWriteLockById(@Param("id") id: Int): Optional<SystemPhaseEntity>
}
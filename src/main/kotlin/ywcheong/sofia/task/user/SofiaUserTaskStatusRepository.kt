package ywcheong.sofia.task.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SofiaUserTaskStatusRepository : JpaRepository<SofiaUserTaskStatus, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ut FROM SofiaUserTaskStatus ut WHERE ut.isResting = false ORDER BY ut.lastAssignedAt ASC LIMIT 1")
    fun findNextAssignee(): SofiaUserTaskStatus?

    fun countByIsRestingFalse(): Int
}

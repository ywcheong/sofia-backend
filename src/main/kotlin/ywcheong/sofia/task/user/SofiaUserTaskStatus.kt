package ywcheong.sofia.task.user

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import ywcheong.sofia.user.SofiaUser
import java.time.Instant
import java.util.*

@Entity
class SofiaUserTaskStatus(
    user: SofiaUser,
    isResting: Boolean = false,
    lastAssignedAt: Instant = Instant.EPOCH,
    warningCount: Int = 0,
    adjustedCharCount: Int = 0,
) {
    @Id
    private var id: UUID = user.id          // user PK를 공유

    @OneToOne(fetch = FetchType.EAGER)
    @MapsId                          // id 필드를 user의 PK로 매핑
    @JoinColumn(name = "id")
    var user: SofiaUser = user
        protected set

    var rest: Boolean = isResting
        protected set

    var lastAssignedAt: Instant = lastAssignedAt
    // protected set // public

    var warningCount: Int = warningCount
        protected set

    var adjustedCharCount: Int = adjustedCharCount
        protected set

    fun updateRestStatus(newResting: Boolean) {
        this.rest = newResting
    }

    fun addWarning() {
        this.warningCount += 1
    }

    fun adjustCharCount(amount: Int) {
        this.adjustedCharCount += amount
    }
}
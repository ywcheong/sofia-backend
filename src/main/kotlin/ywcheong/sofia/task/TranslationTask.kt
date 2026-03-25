package ywcheong.sofia.task

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import ywcheong.sofia.user.SofiaUser
import java.time.Instant
import java.util.UUID

@Entity
class TranslationTask(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    val taskType: TaskType,

    @Column(length = 50)
    val taskDescription: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    var assignee: SofiaUser,

    @Enumerated(EnumType.STRING)
    var assignmentType: AssignmentType,

    val assignedAt: Instant = Instant.now(),

    var completedAt: Instant? = null,

    var characterCount: Int? = null,

    var remindedAt: Instant? = null,
) {
    val completed: Boolean
        get() = completedAt != null

    val reminded: Boolean
        get() = remindedAt != null

    fun markAsReminded(at: Instant = Instant.now()) {
        this.remindedAt = at
    }

    fun changeAssignee(newAssignee: SofiaUser, newAssignmentType: AssignmentType) {
        check(completedAt == null) { "완료된 과제는 담당자를 변경할 수 없습니다." }
        this.assignee = newAssignee
        this.assignmentType = newAssignmentType
    }

    enum class TaskType {
        GAONNURI_POST,
        EXTERNAL_POST,
    }

    enum class AssignmentType {
        AUTOMATIC,
        MANUAL,
    }
}

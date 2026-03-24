package ywcheong.sofia.user.registration

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.util.UUID

@Entity
class UserRegistration (
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true)
    val studentNumber: String,
    val studentName: String,
    val plusfriendUserKey: String,
)
package ywcheong.sofia.user.auth

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.SofiaUserRole
import java.util.*

@Entity
class SofiaUserAuth(
    user: SofiaUser,
    role: SofiaUserRole,
    plusfriendUserKey: String,
    secretToken: UUID = UUID.randomUUID(),
) {
    init {
        require(plusfriendUserKey.isNotBlank()) {
            "plusfriendUserKey는 blank일 수 없습니다."
        }
    }

    @Id
    private var id: UUID = user.id

    @OneToOne(fetch = FetchType.EAGER)
    @MapsId
    @JoinColumn(name = "id")
    var user: SofiaUser = user
        protected set

    @Enumerated(EnumType.STRING)
    var role: SofiaUserRole = role
        protected set

    var plusfriendUserKey: String = plusfriendUserKey
        protected set

    var secretToken: UUID = secretToken
        protected set

    fun regenerateSecretToken(): UUID {
        this.secretToken = UUID.randomUUID()
        return this.secretToken
    }

    fun updateRole(newRole: SofiaUserRole) {
        this.role = newRole
    }
}
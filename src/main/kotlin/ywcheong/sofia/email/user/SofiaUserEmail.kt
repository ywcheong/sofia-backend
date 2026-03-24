package ywcheong.sofia.email.user

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import ywcheong.sofia.user.SofiaUser
import java.util.*

@Entity
class SofiaUserEmail(
    user: SofiaUser,
    unsubscribeToken: UUID = UUID.randomUUID(),
    isUnsubscribed: Boolean = false,
    email: String,
) {
    @Id
    private var id: UUID = user.id

    @OneToOne(fetch = FetchType.EAGER)
    @MapsId
    @JoinColumn(name = "id")
    var user: SofiaUser = user
        protected set

    var unsubscribeToken: UUID = unsubscribeToken
        protected set

    var isUnsubscribed: Boolean = isUnsubscribed
        protected set

    var email: String = email
        protected set

    fun subscribe() {
        this.isUnsubscribed = false
    }

    fun unsubscribe() {
        this.isUnsubscribed = true
    }
}

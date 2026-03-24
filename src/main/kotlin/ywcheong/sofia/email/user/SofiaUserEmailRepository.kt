package ywcheong.sofia.email.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SofiaUserEmailRepository : JpaRepository<SofiaUserEmail, UUID> {
    fun findByUnsubscribeToken(token: UUID): SofiaUserEmail?
}

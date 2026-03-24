package ywcheong.sofia.user.auth

import org.springframework.data.jpa.repository.JpaRepository
import ywcheong.sofia.user.SofiaUserRole
import java.util.Optional
import java.util.UUID

interface SofiaUserAuthRepository : JpaRepository<SofiaUserAuth, UUID> {
    fun findBySecretToken(secretToken: UUID): Optional<SofiaUserAuth>
    fun findByPlusfriendUserKey(plusfriendUserKey: String): Optional<SofiaUserAuth>
    fun countByRole(role: SofiaUserRole): Long
    fun findAllByRole(role: SofiaUserRole): List<SofiaUserAuth>
}
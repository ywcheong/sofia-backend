package ywcheong.sofia.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SofiaUserRepository : JpaRepository<SofiaUser, UUID> {
    fun existsByStudentNumber(studentNumber: String): Boolean
    fun findByAuthPlusfriendUserKey(plusfriendUserKey: String): SofiaUser?
}
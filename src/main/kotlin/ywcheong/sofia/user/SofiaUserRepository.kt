package ywcheong.sofia.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SofiaUserRepository : JpaRepository<SofiaUser, UUID>, JpaSpecificationExecutor<SofiaUser> {
    fun existsByStudentNumber(studentNumber: String): Boolean
    fun findByAuthPlusfriendUserKey(plusfriendUserKey: String): SofiaUser?

    fun findByAuthRole(role: SofiaUserRole): List<SofiaUser>

    fun deleteByAuthRole(role: SofiaUserRole)
}
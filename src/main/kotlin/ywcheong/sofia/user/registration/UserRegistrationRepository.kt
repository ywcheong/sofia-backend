package ywcheong.sofia.user.registration

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRegistrationRepository : JpaRepository<UserRegistration, UUID> {
    fun existsByStudentNumber(studentNumber: String): Boolean
    fun findByPlusfriendUserKey(plusfriendUserKey: String): UserRegistration?
}
package ywcheong.sofia.user

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import ywcheong.sofia.email.user.SofiaUserEmail
import ywcheong.sofia.task.user.SofiaUserTaskStatus
import ywcheong.sofia.user.auth.SofiaUserAuth
import java.util.UUID

@Entity
class SofiaUser(
    id: UUID = UUID.randomUUID(),
    studentNumber: String,
    studentName: String,
    plusfriendUserKey: String,
    role: SofiaUserRole = SofiaUserRole.STUDENT,
) {
    @Id
    var id: UUID = id
        protected set

    @Column(unique = true)
    var studentNumber: String = studentNumber
        protected set

    var studentName: String = studentName
        protected set

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    lateinit var taskStatus: SofiaUserTaskStatus

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    lateinit var auth: SofiaUserAuth

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    lateinit var email: SofiaUserEmail

    init {
        createInitialUserSubInfos(role, plusfriendUserKey) // User에 딸린 정보들을 추가
    }

    private fun createInitialUserSubInfos(role: SofiaUserRole, plusfriendUserKey: String) {
        taskStatus = SofiaUserTaskStatus(user = this)
        auth = SofiaUserAuth(user = this, role = role, plusfriendUserKey = plusfriendUserKey)
        email = SofiaUserEmail(user = this, email = "${studentNumber}@ksa.hs.kr")
    }
}
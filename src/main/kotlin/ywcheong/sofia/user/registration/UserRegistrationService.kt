package ywcheong.sofia.user.registration

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.email.EmailSendService
import ywcheong.sofia.email.templates.RegistrationApprovedEmailTemplate
import ywcheong.sofia.email.templates.RegistrationRejectedEmailTemplate
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.SofiaUserRepository
import ywcheong.sofia.user.auth.SofiaUserAuthRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

interface UserRegistrationService {
    data class ApplyCommand(
        val studentNumber: String,
        val studentName: String,
        val plusfriendUserKey: String,
    ) {
        init {
            if (!studentNumber.matches(STUDENT_NUMBER_PATTERN)) {
                throw BusinessException("학번은 00-000 형식이어야 합니다.")
            }

            if (!studentName.matches(STUDENT_NAME_PATTERN)) {
                throw BusinessException("이름은 2~30자의 한글, 영어 대소문자, 띄어쓰기만 허용됩니다.")
            }
        }

        companion object {
            private val STUDENT_NUMBER_PATTERN = Regex("\\d{2}-\\d{3}")
            private val STUDENT_NAME_PATTERN = Regex("^[가-힣a-zA-Z ]{2,30}$")
        }
    }

    /** 모든 회원가입 신청 목록을 페이지네이션으로 조회합니다. */
    fun findAllRegistrations(pageable: Pageable): Page<UserRegistrationController.RegistrationSummaryResponse>

    /** 회원가입을 요청한 유저를 승인해, 정식 유저로 승격합니다. */
    fun applyRegistration(command: ApplyCommand): UUID

    /** 회원가입을 요청한 유저를 승인해, 정식 유저로 승격합니다. */
    fun acceptRegistration(registrationId: UUID): SofiaUser

    /** 회원가입을 요청한 유저를 거부해, 신청을 삭제합니다. */
    fun denyRegistration(registrationId: UUID)

    /** 회원가입 신청자가 스스로 신청을 취소합니다. */
    fun cancelRegistration(registrationId: UUID)

    /** plusfriendUserKey로 회원가입 신청을 취소합니다. 승인된 사용자는 취소할 수 없습니다. */
    fun cancelRegistrationByPlusfriendUserKey(plusfriendUserKey: String)

    @Service
    class DefaultUserRegistrationService(
        private val userRegistrationRepository: UserRegistrationRepository,
        private val sofiaUserRepository: SofiaUserRepository,
        private val sofiaUserAuthRepository: SofiaUserAuthRepository,
        private val emailSendService: EmailSendService,
    ) : UserRegistrationService {
        private val logger = KotlinLogging.logger {}

        override fun findAllRegistrations(pageable: Pageable): Page<UserRegistrationController.RegistrationSummaryResponse> {
            return userRegistrationRepository.findAll(pageable).map { registration ->
                UserRegistrationController.RegistrationSummaryResponse(
                    id = registration.id,
                    studentNumber = registration.studentNumber,
                    studentName = registration.studentName,
                )
            }
        }

        @Transactional
        override fun applyRegistration(command: ApplyCommand): UUID {
            logger.info { "회원가입 신청: studentNumber=${command.studentNumber}, studentName=${command.studentName}" }

            if (sofiaUserRepository.existsByStudentNumber(command.studentNumber)) {
                throw BusinessException("이미 가입된 학번입니다.")
            }
            if (userRegistrationRepository.existsByStudentNumber(command.studentNumber)) {
                throw BusinessException("이미 신청된 학번입니다.")
            }

            val registration = UserRegistration(
                studentNumber = command.studentNumber,
                studentName = command.studentName,
                plusfriendUserKey = command.plusfriendUserKey,
            )
            userRegistrationRepository.save(registration)

            return registration.id.also {
                logger.info { "회원가입 신청 완료: registrationId=${it}" }
            }
        }

        @Transactional
        override fun acceptRegistration(registrationId: UUID): SofiaUser {
            logger.info { "회원가입 승인 시작: registrationId=$registrationId" }

            val registration =
                userRegistrationRepository.findByIdOrNull(registrationId) ?: throw BusinessException("가입신청을 찾을 수 없습니다.")

            userRegistrationRepository.delete(registration)
            val activeUser = sofiaUserRepository.save(registration.toActiveUser())

            // US-028: 가입 승인 이메일 발송
            sendRegistrationApprovedEmail(activeUser)

            return activeUser.also {
                logger.info { "회원가입 승인 완료: activeUserId=${it.id}, studentNumber=${it.studentNumber}" }
            }
        }

        @Transactional
        override fun denyRegistration(registrationId: UUID) {
            logger.info { "회원가입 거부: registrationId=$registrationId" }
            val registration =
                userRegistrationRepository.findByIdOrNull(registrationId) ?: throw BusinessException("가입신청을 찾을 수 없습니다.")

            // US-028: 가입 거절 이메일 발송 (삭제 전)
            sendRegistrationRejectedEmail(registration)

            userRegistrationRepository.delete(registration)
        }

        @Transactional
        override fun cancelRegistration(registrationId: UUID) {
            logger.info { "회원가입 신청 취소: registrationId=$registrationId" }
            val registration =
                userRegistrationRepository.findByIdOrNull(registrationId) ?: throw BusinessException("가입신청을 찾을 수 없습니다.")
            userRegistrationRepository.delete(registration)
        }

        @Transactional
        override fun cancelRegistrationByPlusfriendUserKey(plusfriendUserKey: String) {
            logger.info { "회원가입 신청 취소: plusfriendUserKey=$plusfriendUserKey" }

            // 이미 승인된 사용자인지 확인
            if (sofiaUserAuthRepository.findByPlusfriendUserKey(plusfriendUserKey).isPresent) {
                throw BusinessException("승인된 번역버디는 가입 취소가 불가합니다. 관리자에게 문의하세요.")
            }

            val registration = userRegistrationRepository.findByPlusfriendUserKey(plusfriendUserKey)
                ?: throw BusinessException("가입 요청을 한 적이 없습니다. 취소가 불가능합니다.")

            userRegistrationRepository.delete(registration)
            logger.info { "회원가입 신청 취소 완료: plusfriendUserKey=$plusfriendUserKey" }
        }

        private fun UserRegistration.toActiveUser() = SofiaUser(
            studentNumber = this.studentNumber,
            studentName = this.studentName,
            plusfriendUserKey = this.plusfriendUserKey,
        )

        private fun sendRegistrationApprovedEmail(user: SofiaUser) {
            val template = RegistrationApprovedEmailTemplate(
                recipientEmail = user.email.email,
                recipientName = user.studentName,
                unsubscribeToken = user.email.unsubscribeToken.toString(),
                approvedAt = formatDateTime(Instant.now()),
            )
            emailSendService.sendEmail(template)
        }

        private fun sendRegistrationRejectedEmail(registration: UserRegistration) {
            // 신청자는 아직 정식 사용자가 아니므로 unsubscribe 불가
            val template = RegistrationRejectedEmailTemplate(
                recipientEmail = "${registration.studentNumber}@ksa.hs.kr",
                recipientName = registration.studentName,
                unsubscribeToken = UUID.randomUUID().toString(), // 임시 토큰
                rejectionReason = "가입 신청이 거절되었습니다.",
                rejectedAt = formatDateTime(Instant.now()),
            )
            emailSendService.sendEmail(template)
        }

        private fun formatDateTime(instant: Instant): String {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.of("Asia/Seoul"))
            return formatter.format(instant)
        }
    }
}
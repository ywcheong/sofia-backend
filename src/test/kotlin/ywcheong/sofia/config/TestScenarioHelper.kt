package ywcheong.sofia.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import ywcheong.sofia.email.FakeJavaMailSender
import ywcheong.sofia.email.user.SofiaUserEmailRepository
import ywcheong.sofia.glossary.GlossaryEntry
import ywcheong.sofia.glossary.GlossaryRepository
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.phase.SystemPhaseEntity
import ywcheong.sofia.phase.SystemPhaseRepository
import ywcheong.sofia.task.TranslationTask
import ywcheong.sofia.task.TranslationTaskRepository
import ywcheong.sofia.task.user.SofiaUserTaskStatusRepository
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.SofiaUserRepository
import ywcheong.sofia.user.SofiaUserRole
import ywcheong.sofia.user.auth.SofiaUserAuthRepository
import ywcheong.sofia.user.registration.UserRegistration
import ywcheong.sofia.user.registration.UserRegistrationRepository
import java.util.*
import kotlin.math.log

/**
 * 테스트 시나리오 설정을 간소화하는 유틸리티 클래스.
 *
 * 반복되는 권한 세팅, 페이즈 설정, DB 초기화 작업을 쉽게 처리할 수 있습니다.
 *
 * ## 사용 규칙
 * - 테스트 코드에서 이 클래스의 메서드를 자유롭게 사용하세요.
 * - 테스트 코드에서 **절대 직접 Repository에 접근하지 마세요.**
 *   공개 API로 대체 불가능한 기능이 필요하면, 이 클래스에 구현한 후 사용하세요.
 * - 이 클래스의 구현 자체도 가급적 MockMvc를 통한 공개 API 호출로 구성하는 것을 권장합니다.
 */
@Component
class TestScenarioHelper(
    private val translationTaskRepository: TranslationTaskRepository,
    private val sofiaUserRepository: SofiaUserRepository,
    private val sofiaUserAuthRepository: SofiaUserAuthRepository,
    private val sofiaUserEmailRepository: SofiaUserEmailRepository,
    private val sofiaUserTaskStatusRepository: SofiaUserTaskStatusRepository,
    private val systemPhaseRepository: SystemPhaseRepository,
    private val userRegistrationRepository: UserRegistrationRepository,
    private val glossaryRepository: GlossaryRepository,
    private val fakeJavaMailSender: FakeJavaMailSender,
) {
    private val logger = KotlinLogging.logger { }

    companion object {
        const val DEFAULT_KAKAO_SECRET = "replace-me-11!!"
    }

    init {
        logger.warn { "테스트 환경이 감지되었습니다. TestScenarioHelper가 활성화되었습니다." }
    }

    /**
     * 모든 테스트 데이터를 초기화합니다.
     * 리포지토리 간의 외래키 제약조건을 고려하여 삭제 순서를 처리합니다.
     */
    fun clearAll() {
        translationTaskRepository.deleteAllInBatch()
        glossaryRepository.deleteAllInBatch()
        sofiaUserAuthRepository.deleteAllInBatch()
        sofiaUserTaskStatusRepository.deleteAllInBatch()
        sofiaUserEmailRepository.deleteAllInBatch()
        sofiaUserRepository.deleteAllInBatch()
        userRegistrationRepository.deleteAllInBatch()
        fakeJavaMailSender.clear()
    }

    /**
     * 용어집 데이터를 초기화합니다.
     */
    fun clearGlossary() {
        glossaryRepository.deleteAllInBatch()
    }

    /**
     * 용어집 항목을 생성합니다.
     */
    fun createGlossaryEntry(koreanTerm: String, englishTerm: String): GlossaryEntry =
        glossaryRepository.save(GlossaryEntry(originalKoreanTerm = koreanTerm, englishTerm = englishTerm))

    /**
     * 시스템 페이즈를 설정합니다.
     */
    fun setPhase(phase: SystemPhase) {
        val entity = systemPhaseRepository.findById(SystemPhaseEntity.PHASE_ENTITY_ID).orElseThrow()
        entity.currentPhase = phase
        systemPhaseRepository.saveAndFlush(entity)
    }

    /**
     * 관리자 계정을 생성하고 인증 토큰을 반환합니다.
     *
     * @param studentNumber 학번 (기본값: "admin")
     * @param studentName 이름 (기본값: "관리자")
     * @return 관리자 계정의 인증 토큰
     */
    fun createAdminAndGetToken(
        studentNumber: String = "admin",
        studentName: String = "관리자"
    ): AdminAuthInfo {
        val user = sofiaUserRepository.save(
            SofiaUser(
                studentNumber = studentNumber,
                studentName = studentName,
                plusfriendUserKey = "test-admin-key-$studentNumber",
                role = SofiaUserRole.ADMIN
            )
        )
        val auth = sofiaUserAuthRepository.findById(user.id).orElseThrow()
        return AdminAuthInfo(
            userId = user.id,
            secretToken = auth.secretToken,
            user = user
        )
    }

    /**
     * 일반 학생 계정을 생성하고 인증 토큰을 반환합니다.
     */
    fun createStudentAndGetToken(
        studentNumber: String,
        studentName: String
    ): AdminAuthInfo {
        val user = sofiaUserRepository.save(
            SofiaUser(
                studentNumber = studentNumber,
                studentName = studentName,
                plusfriendUserKey = "test-user-key-$studentNumber",
                role = SofiaUserRole.STUDENT
            )
        )
        val auth = sofiaUserAuthRepository.findById(user.id).orElseThrow()
        return AdminAuthInfo(
            userId = user.id,
            secretToken = auth.secretToken,
            user = user
        )
    }

    /**
     * 활성 상태의 일반 학생(번역버디)을 생성합니다.
     * 과제 배정 대상이 되는 학생을 생성할 때 사용합니다.
     */
    fun createActiveStudent(studentNumber: String, studentName: String): SofiaUser =
        sofiaUserRepository.save(
            SofiaUser(
                studentNumber = studentNumber,
                studentName = studentName,
                plusfriendUserKey = "test-user-key-$studentNumber",
            )
        )

    /**
     * 이미 가입된 학생을 생성합니다.
     * "이미 가입된 학번으로 신청" 테스트 시나리오에 사용합니다.
     */
    fun createExistingStudent(
        studentNumber: String,
        studentName: String,
        plusfriendUserKey: String = "existing-user-key-$studentNumber"
    ): SofiaUser =
        sofiaUserRepository.save(
            SofiaUser(
                studentNumber = studentNumber,
                studentName = studentName,
                plusfriendUserKey = plusfriendUserKey,
            )
        )

    /**
     * 번역 과제를 생성합니다.
     */
    fun createTranslationTask(
        taskType: TranslationTask.TaskType,
        taskDescription: String,
        assignee: SofiaUser,
        assignmentType: TranslationTask.AssignmentType = TranslationTask.AssignmentType.AUTOMATIC,
    ): TranslationTask =
        translationTaskRepository.save(
            TranslationTask(
                taskType = taskType,
                taskDescription = taskDescription,
                assignee = assignee,
                assignmentType = assignmentType,
            )
        )

    /**
     * 과제의 할당 시간을 지정된 시간으로 변경합니다.
     * 리마인더 테스트 등에서 과거 시간으로 설정할 때 사용합니다.
     */
    fun setTaskAssignedAt(taskId: UUID, assignedAt: java.time.Instant) {
        val task = translationTaskRepository.findById(taskId).orElseThrow()
        translationTaskRepository.save(
            TranslationTask(
                id = task.id,
                taskType = task.taskType,
                taskDescription = task.taskDescription,
                assignee = task.assignee,
                assignmentType = task.assignmentType,
                assignedAt = assignedAt,
                completedAt = task.completedAt,
                characterCount = task.characterCount,
                remindedAt = task.remindedAt,
            )
        )
    }

    /**
     * 과제의 리마인더 발송 시간을 지정된 시간으로 설정합니다.
     * remindedAt 필드 테스트 시나리오에 사용합니다.
     */
    fun setTaskRemindedAt(taskId: UUID, remindedAt: java.time.Instant?) {
        val task = translationTaskRepository.findById(taskId).orElseThrow()
        translationTaskRepository.save(
            TranslationTask(
                id = task.id,
                taskType = task.taskType,
                taskDescription = task.taskDescription,
                assignee = task.assignee,
                assignmentType = task.assignmentType,
                assignedAt = task.assignedAt,
                completedAt = task.completedAt,
                characterCount = task.characterCount,
                remindedAt = remindedAt,
            )
        )
    }

    /**
     * 과제의 완료 시간과 글자 수를 설정합니다.
     * 정렬 테스트 시나리오에 사용합니다.
     */
    fun setTaskCompletedAt(taskId: UUID, completedAt: java.time.Instant?, characterCount: Int? = null) {
        val task = translationTaskRepository.findById(taskId).orElseThrow()
        translationTaskRepository.save(
            TranslationTask(
                id = task.id,
                taskType = task.taskType,
                taskDescription = task.taskDescription,
                assignee = task.assignee,
                assignmentType = task.assignmentType,
                assignedAt = task.assignedAt,
                completedAt = completedAt,
                characterCount = characterCount,
                remindedAt = task.remindedAt,
            )
        )
    }

    /**
     * 사용자의 휴식 상태를 설정합니다.
     * 공개 API를 통하지 않고 직접 설정해야 하는 테스트 시나리오에 사용합니다.
     */
    fun setUserResting(userId: UUID, isResting: Boolean) {
        val status = sofiaUserTaskStatusRepository.findById(userId).orElseThrow()
        status.updateRestStatus(isResting)
        sofiaUserTaskStatusRepository.saveAndFlush(status)
    }

    /**
     * 관리자 인증 헤더 값을 생성합니다.
     */
    fun adminAuthHeader(token: UUID): String = "user $token"

    /**
     * 카카오 인증 헤더 값을 생성합니다.
     */
    fun kakaoAuthHeader(secret: String = DEFAULT_KAKAO_SECRET): String = "kakao $secret"

    /**
     * MockMvc 요청에 관리자 인증 헤더를 추가합니다.
     */
    fun MockHttpServletRequestBuilder.withAdminAuth(token: UUID): MockHttpServletRequestBuilder =
        this.header("Authorization", adminAuthHeader(token))

    /**
     * MockMvc 요청에 카카오 인증 헤더를 추가합니다.
     */
    fun MockHttpServletRequestBuilder.withKakaoAuth(secret: String = DEFAULT_KAKAO_SECRET): MockHttpServletRequestBuilder =
        this.header("Authorization", kakaoAuthHeader(secret))

    /**
     * 전체 설정을 한 번에 처리: DB 초기화 + 페이즈 설정
     */
    fun setupScenario(phase: SystemPhase) {
        clearAll()
        setPhase(phase)
    }

    /**
     * 전체 설정을 한 번에 처리: DB 초기화 + 페이즈 설정 + 관리자 계정 생성
     */
    fun setupScenarioWithAdmin(phase: SystemPhase): AdminAuthInfo {
        clearAll()
        setPhase(phase)
        return createAdminAndGetToken()
    }

    /**
     * 관리자 인증 정보를 담는 데이터 클래스
     */
    data class AdminAuthInfo(
        val userId: UUID,
        val secretToken: UUID,
        val user: SofiaUser
    )

    /**
     * 테스트용 JavaMailSender에 접근합니다.
     * 이메일 발송 검증 시 사용합니다.
     */
    fun getMailSender(): FakeJavaMailSender = fakeJavaMailSender

    /**
     * plusfriendUserKey로 회원가입 신청을 조회합니다.
     */
    fun findRegistrationByPlusfriendUserKey(plusfriendUserKey: String): UserRegistration? =
        userRegistrationRepository.findByPlusfriendUserKey(plusfriendUserKey)

    /**
     * 학번으로 회원가입 신청을 조회합니다.
     */
    fun findRegistrationByStudentNumber(studentNumber: String): UserRegistration? =
        userRegistrationRepository.findAll().find { it.studentNumber == studentNumber }
}

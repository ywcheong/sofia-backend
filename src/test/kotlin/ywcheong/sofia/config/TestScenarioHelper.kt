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
     * 과제를 완료 상태로 변경합니다.
     * 완료 시간은 현재 시간으로 설정됩니다.
     */
    fun completeTask(taskId: UUID) {
        val task = translationTaskRepository.findById(taskId).orElseThrow()
        translationTaskRepository.save(
            TranslationTask(
                id = task.id,
                taskType = task.taskType,
                taskDescription = task.taskDescription,
                assignee = task.assignee,
                assignmentType = task.assignmentType,
                assignedAt = task.assignedAt,
                completedAt = java.time.Instant.now(),
                characterCount = task.characterCount,
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

    /**
     * 과제 ID로 과제를 조회합니다.
     */
    fun findTaskById(taskId: UUID): TranslationTask? =
        translationTaskRepository.findById(taskId).orElse(null)

    /**
     * 모든 미완료 과제를 조회합니다.
     */
    fun findAllIncompleteTasks(): List<TranslationTask> =
        translationTaskRepository.findAllIncompleteTasks()

    // ==================== 시나리오별 설정 메서드 ====================

    /**
     * RECRUITMENT 페이즈 시나리오를 설정합니다.
     * - DB 초기화
     * - RECRUITMENT 페이즈 설정
     * - 관리자 계정 생성 (휴식 상태)
     */
    fun setupRecruitmentScenario(): AdminAuthInfo {
        clearAll()
        setPhase(SystemPhase.RECRUITMENT)
        val admin = createAdminAndGetToken()
        setUserResting(admin.userId, true)
        return admin
    }

    /**
     * TRANSLATION 페이즈 시나리오를 설정합니다.
     * - DB 초기화
     * - TRANSLATION 페이즈 설정
     * - 관리자 계정 생성 (휴식 상태)
     */
    fun setupTranslationScenario(): AdminAuthInfo {
        clearAll()
        setPhase(SystemPhase.TRANSLATION)
        val admin = createAdminAndGetToken()
        setUserResting(admin.userId, true)
        return admin
    }

    // ==================== 복합 데이터 생성 메서드 ====================

    /**
     * 지각 과제를 생성합니다.
     * assignedAt을 현재 시간보다 [hoursLate]시간 과거로 설정하여,
     * 이미 할당된 지 오래된 과제 시나리오를 만듭니다.
     *
     * @param studentNumber 학번
     * @param description 과제 설명
     * @param hoursLate 늦은 시간 (시간 단위)
     * @return 생성된 지각 과제
     */
    fun createLateTask(
        studentNumber: String,
        description: String,
        hoursLate: Long
    ): TranslationTask {
        val student = createActiveStudent(studentNumber, "학생-$studentNumber")
        val assignedAt = java.time.Instant.now().minusSeconds(hoursLate * 3600)
        return translationTaskRepository.save(
            TranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = description,
                assignee = student,
                assignmentType = TranslationTask.AssignmentType.AUTOMATIC,
                assignedAt = assignedAt,
            )
        )
    }

    /**
     * 완료된 과제를 생성합니다.
     * completedAt을 현재 시간으로, characterCount를 지정된 값으로 설정합니다.
     *
     * @param studentNumber 학번
     * @param description 과제 설명
     * @param charCount 글자 수
     * @return 생성된 완료 과제
     */
    fun createCompletedTask(
        studentNumber: String,
        description: String,
        charCount: Int
    ): TranslationTask {
        val student = createActiveStudent(studentNumber, "학생-$studentNumber")
        return translationTaskRepository.save(
            TranslationTask(
                taskType = TranslationTask.TaskType.GAONNURI_POST,
                taskDescription = description,
                assignee = student,
                assignmentType = TranslationTask.AssignmentType.AUTOMATIC,
                completedAt = java.time.Instant.now(),
                characterCount = charCount,
            )
        )
    }

    // ==================== 일괄 검증 메서드 ====================

    /**
     * 이메일 발송을 검증합니다.
     * 제목이 일치하고 본문에 지정된 내용이 포함된 이메일이 발송되었는지 확인합니다.
     *
     * @param subject 이메일 제목 (부분 일치)
     * @param contentContains 본문에 포함되어야 하는 내용
     * @throws AssertionError 이메일이 발송되지 않았거나 내용이 일치하지 않는 경우
     */
    fun assertEmailSent(subject: String, contentContains: String) {
        val messages = fakeJavaMailSender.getMessagesBySubject(subject)
        check(messages.isNotEmpty()) { "제목이 '$subject'인 이메일이 발송되지 않았습니다." }

        val found = messages.any { message ->
            val content = fakeJavaMailSender.extractContent(message)
            content.contains(contentContains)
        }
        check(found) { "제목이 '$subject'인 이메일 중 본문에 '$contentContains'가 포함된 것이 없습니다." }
    }
}

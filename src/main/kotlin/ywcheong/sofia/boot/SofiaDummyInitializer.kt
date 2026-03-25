package ywcheong.sofia.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import ywcheong.sofia.glossary.GlossaryService
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.phase.SystemPhaseService
import ywcheong.sofia.task.TranslationTask
import ywcheong.sofia.task.TranslationTaskService
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.UserManagementService
import ywcheong.sofia.user.registration.UserRegistrationService
import java.util.*

@Component
@Order(20)
@ConditionalOnProperty(prefix = "sofia.dummy", name = ["enabled"], havingValue = "true")
class SofiaDummyInitializer(
    private val userManagementService: UserManagementService,
    private val taskService: TranslationTaskService,
    private val glossaryService: GlossaryService,
    private val registrationService: UserRegistrationService,
    private val systemPhaseService: SystemPhaseService,
) : CommandLineRunner {

    private val logger = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        logger.warn { "[더미 모드] 더미 데이터 생성을 시작합니다..." }

        createTasks(createAdmins() + createStudents())
        createGlossaryEntries()
        systemPhaseService.transitToRecruitment()
        systemPhaseService.transitToTranslation()
        createRegistrations()

        logger.warn {
            """
            |[더미 모드] 더미 데이터 생성 완료!
            |   - 관리자: 2명
            |   - 학생: 10명
            |   - 작업: 30개
            |   - 용어집: 20개
            |   - 가입 신청: 5개
            """.trimMargin()
        }
    }

    private fun createAdmins(): List<SofiaUser> {
        val adminData = listOf(
            "99-001" to "김관리",
            "99-002" to "이관리",
        )

        return adminData.map { (studentNumber, name) ->
            val registrationId = registrationService.applyRegistration(
                UserRegistrationService.ApplyCommand(
                    studentNumber = studentNumber,
                    studentName = name,
                    plusfriendUserKey = "dummy-admin-$studentNumber",
                )
            )
            val user = registrationService.acceptRegistration(registrationId)
            userManagementService.promoteToAdmin(
                UserManagementService.PromoteToAdminCommand(userId = user.id)
            )
        }
    }

    private fun createStudents(): List<SofiaUser> {
        val studentData = listOf(
            "99-101" to "김철수",
            "99-102" to "이영희",
            "99-103" to "박민수",
            "99-104" to "최수진",
            "99-105" to "정현우",
            "99-106" to "강지영",
            "99-107" to "윤성민",
            "99-108" to "장서연",
            "99-109" to "임준혁",
            "99-110" to "한소희",
        )

        return studentData.map { (studentNumber, name) ->
            val registrationId = registrationService.applyRegistration(
                UserRegistrationService.ApplyCommand(
                    studentNumber = studentNumber,
                    studentName = name,
                    plusfriendUserKey = "dummy-student-$studentNumber",
                )
            )
            registrationService.acceptRegistration(registrationId)
        }
    }

    private fun createTasks(users: List<SofiaUser>) {
        val taskDescriptions = listOf(
            "가온누리 공지사항 3월 첫째 주",
            "가온누리 공지사항 3월 둘째 주",
            "가온누리 공지사항 3월 셋째 주",
            "가온누리 입시 정보 1월호",
            "가온누리 입시 정보 2월호",
            "가온누리 입시 정보 3월호",
            "가온누리 장학금 안내 1",
            "가온누리 장학금 안내 2",
            "가온누리 동아리 활동 보고",
            "가온누리 행사 공지 - 개학식",
            "외부 기사 - 과학동아 3월호",
            "외부 기사 - 수학동아 3월호",
            "외부 기사 - 영어교육 뉴스",
            "외부 기사 - 진로 탐색 가이드",
            "외부 기사 - 대입 전형 분석",
            "외부 기사 - 학생부 종합전형",
            "외부 기사 - 논술 가이드",
            "외부 기사 - 면접 준비 팁",
            "외부 기사 - 교과 우수자 전형",
            "외부 기사 - 특기자 전형 안내",
            "가온누리 방과후 학교 안내",
            "가온누리 교내 대회 결과",
            "가온누리 학생회 활동 보고",
            "가온누리 방송부 주간 소식",
            "가온누리 도서관 신간 안내",
            "외부 기사 - 진로 로드맵",
            "외부 기사 - 학습 전략 가이드",
            "외부 기사 - 시간 관리 팁",
            "외부 기사 - 독서 교육 뉴스",
            "외부 기사 - 창의적 체험활동",
        )

        val taskTypes = TranslationTask.TaskType.entries
        val assignmentTypes = TranslationTask.AssignmentType.entries

        taskDescriptions.forEachIndexed { index, description ->
            val taskType = taskTypes[index % taskTypes.size]
            val assignee = users[index % users.size]
            val assignmentType = assignmentTypes[index % assignmentTypes.size]

            taskService.createTask(
                TranslationTaskService.CreateTaskCommand(
                    taskType = taskType,
                    taskDescription = description,
                    assignmentType = assignmentType,
                    assigneeId = if (assignmentType == TranslationTask.AssignmentType.MANUAL) assignee.id else null,
                )
            )
        }
    }

    private fun createGlossaryEntries() {
        val glossaryData = listOf(
            "학생부 종합전형" to "Comprehensive Student Record Screening",
            "학교생활기록부" to "School Activity Record",
            "자기소개서" to "Personal Statement",
            "교과 성적" to "Subject Academic Achievement",
            "비교과 활동" to "Non-Curricular Activities",
            "진로 희망" to "Career Aspiration",
            "창의적 체험활동" to "Creative Experiential Activities",
            "동아리 활동" to "Club Activities",
            "봉사 활동" to "Volunteer Activities",
            "진로 탐색" to "Career Exploration",
            "심화 학습" to "Advanced Learning",
            "융합 탐구" to "Convergent Inquiry",
            "독서 활동" to "Reading Activities",
            "표현 활동" to "Expressive Activities",
            "예체능 활동" to "Arts and Physical Activities",
            "정보 소양" to "Information Literacy",
            "커뮤니케이션 능력" to "Communication Skills",
            "협력적 문제 해결" to "Collaborative Problem Solving",
            "비판적 사고" to "Critical Thinking",
            "자기 주도 학습" to "Self-Directed Learning",
        )

        glossaryData.forEach { (korean, english) ->
            glossaryService.create(
                GlossaryService.CreateCommand(
                    koreanTerm = korean,
                    englishTerm = english,
                )
            )
        }
    }

    private fun createRegistrations(): List<UUID> {
        val registrationData = listOf(
            "99-201" to "신청자갑",
            "99-202" to "신청자을",
            "99-203" to "신청자병",
            "99-204" to "신청자정",
            "99-205" to "신청자무",
        )

        return registrationData.map { (studentNumber, name) ->
            registrationService.applyRegistration(
                UserRegistrationService.ApplyCommand(
                    studentNumber = studentNumber,
                    studentName = name,
                    plusfriendUserKey = "dummy-registration-$studentNumber",
                )
            )
        }
    }
}

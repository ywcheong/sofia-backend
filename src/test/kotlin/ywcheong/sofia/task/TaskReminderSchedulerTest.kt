package ywcheong.sofia.task

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.task.TranslationTask.AssignmentType
import ywcheong.sofia.task.TranslationTask.TaskType
import java.time.Instant
import java.util.UUID

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("과제 리마인더 스케줄러")
class TaskReminderSchedulerTest(
    private val helper: TestScenarioHelper,
    private val taskReminderScheduler: TaskReminderScheduler,
    private val translationTaskRepository: TranslationTaskRepository,
) {

    @BeforeEach
    fun setUp() {
        helper.setupScenario(SystemPhase.TRANSLATION)
    }

    @Nested
    @DisplayName("48시간 경과 리마인더 발송")
    inner class SendReminders {

        @Test
        fun `48시간 경과 미완료 과제는 리마인드 대상이 된다`() {
            // given - 49시간 전에 할당된 미완료 과제
            val student = helper.createActiveStudent("25-001", "홍길동")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "리마인더 대상 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            val assignedAt = Instant.now().minusSeconds(49 * 60 * 60) // 49시간 전
            helper.setTaskAssignedAt(task.id, assignedAt)

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 리마인드 표시됨
            val updatedTask = translationTaskRepository.findById(task.id).orElseThrow()
            assertThat(updatedTask.isReminded).isTrue()
            assertThat(updatedTask.remindedAt).isNotNull()
        }

        @Test
        fun `48시간이 지나지 않은 과제는 리마인드 대상이 아니다`() {
            // given - 47시간 전에 할당된 미완료 과제
            val student = helper.createActiveStudent("25-002", "김철수")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "아직 리마인더 대상 아님",
                student,
                AssignmentType.AUTOMATIC,
            )
            val assignedAt = Instant.now().minusSeconds(47 * 60 * 60) // 47시간 전
            helper.setTaskAssignedAt(task.id, assignedAt)

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 리마인드되지 않음
            val updatedTask = translationTaskRepository.findById(task.id).orElseThrow()
            assertThat(updatedTask.isReminded).isFalse()
            assertThat(updatedTask.remindedAt).isNull()
        }

        @Test
        fun `이미 완료된 과제는 리마인드 대상이 아니다`() {
            // given - 49시간 전에 할당됐지만 이미 완료된 과제
            val adminInfo = helper.createAdminAndGetToken()
            val student = helper.createActiveStudent("25-003", "박영희")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "완료된 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            val assignedAt = Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, assignedAt)

            // 과제 완료 처리
            completeTask(task.id)

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 리마인드되지 않음
            val updatedTask = translationTaskRepository.findById(task.id).orElseThrow()
            assertThat(updatedTask.isReminded).isFalse()
        }

        @Test
        fun `이미 리마인드된 과제는 다시 리마인드하지 않는다`() {
            // given - 이미 리마인드된 과제
            val student = helper.createActiveStudent("25-004", "이민수")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "이미 리마인드된 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            val assignedAt = Instant.now().minusSeconds(50 * 60 * 60) // 50시간 전
            helper.setTaskAssignedAt(task.id, assignedAt)

            // 첫 번째 리마인드
            taskReminderScheduler.checkAndSendReminders()
            val firstRemindedAt = translationTaskRepository.findById(task.id).orElseThrow().remindedAt

            // 추가 시간 경과 시뮬레이션 - assignedAt을 더 과거로
            helper.setTaskAssignedAt(task.id, Instant.now().minusSeconds(72 * 60 * 60))

            // when - 두 번째 리마인더 시도
            taskReminderScheduler.checkAndSendReminders()

            // then - remindedAt이 변경되지 않음 (기존 리마인드 기록 유지)
            val updatedTask = translationTaskRepository.findById(task.id).orElseThrow()
            assertThat(updatedTask.remindedAt).isEqualTo(firstRemindedAt)
        }

        @Test
        fun `리마인더 대상이 없으면 아무 일도 일어나지 않는다`() {
            // given - 새로 할당된 과제만 있음 (48시간 미경과)
            val student = helper.createActiveStudent("25-005", "정우성")
            helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "새 과제",
                student,
                AssignmentType.AUTOMATIC,
            )

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 예외 없이 정상 완료
            // 모든 과제가 리마인드되지 않음
            val allTasks = translationTaskRepository.findAllIncompleteTasks()
            assertThat(allTasks).allMatch { !it.isReminded }
        }

        @Test
        fun `여러 과제가 리마인더 대상이면 모두 처리된다`() {
            // given - 3개의 리마인더 대상 과제
            val student1 = helper.createActiveStudent("25-010", "학생1")
            val student2 = helper.createActiveStudent("25-011", "학생2")
            val student3 = helper.createActiveStudent("25-012", "학생3")

            val task1 = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "과제1",
                student1,
            )
            val task2 = helper.createTranslationTask(
                TaskType.EXTERNAL_POST,
                "과제2",
                student2,
            )
            val task3 = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "과제3",
                student3,
            )

            val oldTime = Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task1.id, oldTime)
            helper.setTaskAssignedAt(task2.id, oldTime)
            helper.setTaskAssignedAt(task3.id, oldTime)

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 모든 과제가 리마인드됨
            val updatedTask1 = translationTaskRepository.findById(task1.id).orElseThrow()
            val updatedTask2 = translationTaskRepository.findById(task2.id).orElseThrow()
            val updatedTask3 = translationTaskRepository.findById(task3.id).orElseThrow()

            assertThat(updatedTask1.isReminded).isTrue()
            assertThat(updatedTask2.isReminded).isTrue()
            assertThat(updatedTask3.isReminded).isTrue()
        }

        @Test
        fun `리마인더 발송 시 관리자에게도 알림 이메일이 발송된다`() {
            // given - 관리자 생성
            val adminInfo = helper.createAdminAndGetToken("admin1", "관리자1")
            val student = helper.createActiveStudent("25-020", "지각학생")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "지연 과제",
                student,
            )
            val assignedAt = Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, assignedAt)

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 관리자에게 알림 이메일 발송됨
            val mailSender = helper.getMailSender()
            val adminNotifications = mailSender.getMessagesBySubject("번역 과제 지연 사용자 알림")
            assertThat(adminNotifications).isNotEmpty()

            // 관리자 이메일로 발송된 메시지 확인
            val adminEmail = "${adminInfo.user.studentNumber}@ksa.hs.kr"
            val sentToAdmin = mailSender.getMessagesTo(adminEmail)
            assertThat(sentToAdmin).isNotEmpty()
        }

        @Test
        fun `여러 관리자가 있으면 모든 관리자에게 알림이 발송된다`() {
            // given - 여러 관리자 생성
            val admin1 = helper.createAdminAndGetToken("admin2", "관리자2")
            val admin2 = helper.createAdminAndGetToken("admin3", "관리자3")
            val student = helper.createActiveStudent("25-021", "지각학생2")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "지연 과제2",
                student,
            )
            val assignedAt = Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, assignedAt)

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 두 관리자 모두에게 알림 발송됨
            val mailSender = helper.getMailSender()
            val admin1Email = "${admin1.user.studentNumber}@ksa.hs.kr"
            val admin2Email = "${admin2.user.studentNumber}@ksa.hs.kr"

            val sentToAdmin1 = mailSender.getMessagesTo(admin1Email)
            val sentToAdmin2 = mailSender.getMessagesTo(admin2Email)

            assertThat(sentToAdmin1).isNotEmpty()
            assertThat(sentToAdmin2).isNotEmpty()
        }

        @Test
        fun `리마인더 발송 시 번역버디에게도 리마인더 이메일이 발송된다`() {
            // given
            val student = helper.createActiveStudent("25-022", "번역버디")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "리마인더 대상 과제",
                student,
            )
            val assignedAt = Instant.now().minusSeconds(49 * 60 * 60)
            helper.setTaskAssignedAt(task.id, assignedAt)

            // when
            taskReminderScheduler.checkAndSendReminders()

            // then - 번역버디에게 리마인더 이메일 발송됨
            val mailSender = helper.getMailSender()
            val studentEmail = "${student.studentNumber}@ksa.hs.kr"
            val sentToStudent = mailSender.getMessagesTo(studentEmail)

            assertThat(sentToStudent).isNotEmpty()
            val emailInfo = mailSender.extractEmailInfo(sentToStudent.first())
            assertThat(emailInfo.subject).contains("알림")
        }
    }

    // 헬퍼 메서드: 과제 완료 처리 (리포지토리 직접 접근)
    // TestScenarioHelper 규칙에 따라 공개 API를 사용해야 하지만,
    // 스케줄러 테스트에서는 MockMvc 의존성이 없으므로 직접 처리
    private fun completeTask(taskId: UUID) {
        val task = translationTaskRepository.findById(taskId).orElseThrow()
        translationTaskRepository.save(
            TranslationTask(
                id = task.id,
                taskType = task.taskType,
                taskDescription = task.taskDescription,
                assignee = task.assignee,
                assignmentType = task.assignmentType,
                assignedAt = task.assignedAt,
                completedAt = Instant.now(),
                characterCount = 1000,
                remindedAt = task.remindedAt,
            )
        )
    }
}

package ywcheong.sofia.task

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.kakao.FakeKakaoMessageSimulator
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.task.TranslationTask.AssignmentType
import ywcheong.sofia.task.TranslationTask.TaskType
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("내 과제 조회 스킬")
class FindMyTasksSkillTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    lateinit var simulator: FakeKakaoMessageSimulator

    @BeforeEach
    fun setUp() {
        helper.setupScenario(SystemPhase.TRANSLATION)
        simulator = FakeKakaoMessageSimulator(
            mockMvc = mockMvc,
            objectMapper = objectMapper,
            testScenarioHelper = helper
        )
    }

    @Nested
    @DisplayName("미완료 과제가 없는 경우")
    inner class NoIncompleteTasks {

        @Test
        fun `미완료 과제가 없으면 안내 메시지를 반환한다`() {
            // given: 과제가 없는 활성 학생
            val student = helper.createActiveStudent("25-001", "홍길동")

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 안내 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("현재 배정된 업무가 없습니다")) { "과제 없음 안내 메시지가 포함되어야 합니다" }
            assert(response.contains("홈 메뉴")) { "홈 메뉴 빠른 응답이 포함되어야 합니다" }
        }

        @Test
        fun `모든 과제가 완료된 경우 안내 메시지를 반환한다`() {
            // given: 완료된 과제만 있는 학생
            val student = helper.createActiveStudent("25-002", "김철수")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "완료된 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskCompletedAt(task.id, Instant.now(), characterCount = 1000)

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 안내 메시지 반환
            val response = result.response.contentAsString
            assert(response.contains("현재 배정된 업무가 없습니다")) { "완료된 과제만 있을 때 안내 메시지가 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("미완료 과제가 있는 경우")
    inner class HasIncompleteTasks {

        @Test
        fun `미완료 과제가 있으면 목록을 반환한다`() {
            // given: 미완료 과제가 있는 학생
            val student = helper.createActiveStudent("25-003", "박영희")
            helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "가온누리 공지사항 번역",
                student,
                AssignmentType.AUTOMATIC,
            )

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 과제 목록 반환
            val response = result.response.contentAsString
            assert(response.contains("현재 해결해야 하는 업무는 다음과 같습니다")) { "업무 안내 메시지가 포함되어야 합니다" }
            assert(response.contains("가온누리 공지사항 번역")) { "과제명이 포함되어야 합니다" }
            assert(response.contains("가온누리")) { "과제 유형(가온누리)이 포함되어야 합니다" }
        }

        @Test
        fun `과제 목록은 오래된 순으로 정렬된다`() {
            // given: 여러 미완료 과제가 있는 학생
            val student = helper.createActiveStudent("25-004", "이민수")
            val oldTask = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "오래된 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(oldTask.id, Instant.now().minusSeconds(24 * 60 * 60)) // 1일 전

            val newTask = helper.createTranslationTask(
                TaskType.EXTERNAL_POST,
                "새로운 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(newTask.id, Instant.now().minusSeconds(1 * 60 * 60)) // 1시간 전

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 오래된 과제가 먼저 표시됨
            val response = result.response.contentAsString
            val oldTaskIndex = response.indexOf("오래된 과제")
            val newTaskIndex = response.indexOf("새로운 과제")
            assert(oldTaskIndex < newTaskIndex) { "오래된 과제가 먼저 표시되어야 합니다" }
        }

        @Test
        fun `외부 과제 유형이 올바르게 표시된다`() {
            // given: 외부 과제가 있는 학생
            val student = helper.createActiveStudent("25-005", "정우성")
            helper.createTranslationTask(
                TaskType.EXTERNAL_POST,
                "외부 문서 번역",
                student,
                AssignmentType.AUTOMATIC,
            )

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 외부 유형 표시
            val response = result.response.contentAsString
            assert(response.contains("외부")) { "외부 과제 유형이 포함되어야 합니다" }
            assert(response.contains("외부 문서 번역")) { "과제명이 포함되어야 합니다" }
        }

        @Test
        fun `경과 시간이 올바르게 표시된다`() {
            // given: 2시간 전에 할당된 과제
            val student = helper.createActiveStudent("25-006", "한지민")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "시간 테스트 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(task.id, Instant.now().minusSeconds(2 * 60 * 60)) // 2시간 전

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 경과 시간 표시
            val response = result.response.contentAsString
            assert(response.contains("2시간")) { "경과 시간이 포함되어야 합니다" }
        }

        @Test
        fun `빠른 응답에 홈 메뉴와 끝난 번역 보고하기가 포함된다`() {
            // given: 미완료 과제가 있는 학생
            val student = helper.createActiveStudent("25-007", "강호동")
            helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "테스트 과제",
                student,
                AssignmentType.AUTOMATIC,
            )

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 빠른 응답 포함
            val response = result.response.contentAsString
            assert(response.contains("홈 메뉴")) { "홈 메뉴 빠른 응답이 포함되어야 합니다" }
            assert(response.contains("끝난 번역 보고하기")) { "끝난 번역 보고하기 빠른 응답이 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("지각된 과제")
    inner class LateTasks {

        @Test
        fun `지각된 과제는 지각 경고 메시지를 포함한다`() {
            // given: 49시간 전에 할당된 미완료 과제 (48시간 임계값 초과)
            val student = helper.createActiveStudent("25-008", "지각학생")
            val lateTask = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "지각된 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(lateTask.id, Instant.now().minusSeconds(49 * 60 * 60)) // 49시간 전

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 지각 경고 메시지 포함
            val response = result.response.contentAsString
            assert(response.contains("마감기한이 지난 업무")) { "지각 경고 메시지가 포함되어야 합니다" }
            assert(response.contains("임의로 삭제될 수 있습니다")) { "삭제 가능 안내가 포함되어야 합니다" }
            assert(response.contains("번역버디장과 연락하세요")) { "번역버디장 연락 안내가 포함되어야 합니다" }
        }

        @Test
        fun `정상 과제와 지각 과제가 함께 있으면 각각 다르게 표시된다`() {
            // given: 정상 과제와 지각 과제가 함께 있는 학생
            val student = helper.createActiveStudent("25-009", "복합학생")

            val normalTask = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "정상 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(normalTask.id, Instant.now().minusSeconds(1 * 60 * 60)) // 1시간 전

            val lateTask = helper.createTranslationTask(
                TaskType.EXTERNAL_POST,
                "지각 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(lateTask.id, Instant.now().minusSeconds(50 * 60 * 60)) // 50시간 전

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 정상 과제는 경고 없이, 지각 과제는 경고와 함께 표시
            val response = result.response.contentAsString
            assert(response.contains("정상 과제")) { "정상 과제명이 포함되어야 합니다" }
            assert(response.contains("지각 과제")) { "지각 과제명이 포함되어야 합니다" }
            assert(response.contains("마감기한이 지난 업무")) { "지각 경고 메시지가 포함되어야 합니다" }
        }

        @Test
        fun `여러 개의 지각 과제가 있어도 모두 경고 메시지가 포함된다`() {
            // given: 여러 지각 과제가 있는 학생
            val student = helper.createActiveStudent("25-010", "다중지각학생")

            val lateTask1 = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "지각 과제 1",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(lateTask1.id, Instant.now().minusSeconds(72 * 60 * 60)) // 72시간 전

            val lateTask2 = helper.createTranslationTask(
                TaskType.EXTERNAL_POST,
                "지각 과제 2",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(lateTask2.id, Instant.now().minusSeconds(60 * 60 * 60)) // 60시간 전

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 모든 지각 과제에 경고 메시지
            val response = result.response.contentAsString
            // 마감기한 경고 메시지가 2번 이상 나타나는지 확인
            val warningCount = response.split("마감기한이 지난 업무").size - 1
            assert(warningCount >= 2) { "모든 지각 과제에 경고 메시지가 포함되어야 합니다 (현재: $warningCount)" }
        }
    }

    @Nested
    @DisplayName("미인증 사용자")
    inner class UnauthenticatedUser {

        @Test
        fun `미인증 사용자가 요청하면 안내 메시지를 반환한다`() {
            // when: 미인증 사용자의 givenwork 요청
            val result = simulator.sendMessage(fromUser = null, action = "givenwork")

            // then: 인증 안내 메시지
            val response = result.response.contentAsString
            assert(response.contains("가입된 번역버디만 사용할 수 있습니다")) { "가입 안내 메시지가 포함되어야 합니다" }
            assert(response.contains("홈 메뉴")) { "홈 메뉴 빠른 응답이 포함되어야 합니다" }
        }
    }

    @Nested
    @DisplayName("경과 시간 포맷팅")
    inner class ElapsedTimeFormatting {

        @Test
        fun `방금 할당된 과제는 방금으로 표시된다`() {
            // given: 방금 할당된 과제
            val student = helper.createActiveStudent("25-011", "신규학생")
            helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "방금 과제",
                student,
                AssignmentType.AUTOMATIC,
            )

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 방금 표시
            val response = result.response.contentAsString
            assert(response.contains("방금")) { "방금 할당된 과제는 '방금'으로 표시되어야 합니다" }
        }

        @Test
        fun `1일 이상 경과한 과제는 분을 생략한다`() {
            // given: 1일 2시간 전에 할당된 과제
            val student = helper.createActiveStudent("25-012", "장기학생")
            val task = helper.createTranslationTask(
                TaskType.GAONNURI_POST,
                "장기 과제",
                student,
                AssignmentType.AUTOMATIC,
            )
            helper.setTaskAssignedAt(task.id, Instant.now().minusSeconds(26 * 60 * 60)) // 26시간 = 1일 2시간

            // when: givenwork 요청
            val result = simulator.sendMessage(fromUser = student, action = "givenwork")

            // then: 일, 시간만 표시 (분 생략)
            val response = result.response.contentAsString
            assert(response.contains("1일")) { "1일 이상 경과한 과제는 일 단위로 표시되어야 합니다" }
            assert(response.contains("2시간")) { "시간 단위도 표시되어야 합니다" }
        }
    }
}

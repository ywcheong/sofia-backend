package ywcheong.sofia.kakao

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
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.task.TranslationTask
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("카카오 스킬")
class KakaoSkillTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    lateinit var simulator: FakeKakaoMessageSimulator

    @BeforeEach
    fun setUp() {
        simulator = FakeKakaoMessageSimulator(
            mockMvc = mockMvc,
            objectMapper = objectMapper,
            testScenarioHelper = helper
        )
    }

    // ==================== 기본 스킬 ====================

    @Nested
    @DisplayName("기본 스킬")
    inner class BasicSkills {

        @BeforeEach
        fun setUpPhase() {
            helper.setupScenario(SystemPhase.RECRUITMENT)
            helper.clearGlossary()
        }

        @Nested
        @DisplayName("healthcheck 액션")
        inner class HealthCheck {

            @Test
            @DisplayName("POST /api/kakao/skill - 등록된 사용자가 healthcheck 요청")
            fun `등록된 사용자가 요청하면 ok 응답을 반환한다`() {
                // given: 등록된 사용자
                val user = helper.createActiveStudent("2024001", "홍길동")

                // when: healthcheck 요청
                val result = simulator.sendMessage(fromUser = user, action = "healthcheck")

                // then: ok 응답
                val response = result.response.contentAsString
                assert(response.contains("ok")) { "응답에 'ok'가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 익명 사용자가 healthcheck 요청")
            fun `익명 사용자가 요청해도 ok 응답을 반환한다`() {
                // when: 익명 사용자의 healthcheck 요청
                val result = simulator.sendMessage(fromUser = null, action = "healthcheck")

                // then: ok 응답
                val response = result.response.contentAsString
                assert(response.contains("ok")) { "응답에 'ok'가 포함되어야 합니다" }
            }
        }

        @Nested
        @DisplayName("존재하지 않는 액션")
        inner class UnknownAction {

            @Test
            @DisplayName("POST /api/kakao/skill - 존재하지 않는 액션 요청")
            fun `등록되지 않은 액션을 요청하면 서버 오류 메시지를 반환한다`() {
                // when: 존재하지 않는 액션 요청
                val result = simulator.sendMessage(fromUser = null, action = "unknown-action")

                // then: 200 OK + 서버 오류 메시지
                val response = result.response.contentAsString
                assert(response.contains("서버 오류가 발생했습니다")) {
                    "존재하지 않는 액션에 대해 서버 오류 메시지를 반환해야 합니다"
                }
            }
        }

        @Nested
        @DisplayName("dictionary 액션")
        inner class Dictionary {

            @Test
            @DisplayName("POST /api/kakao/skill - 텍스트에서 사전 단어를 찾으면 목록을 반환한다")
            fun `텍스트에서 사전 단어를 찾으면 목록을 반환한다`() {
                // given: 사전에 단어 등록
                helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")
                helper.createGlossaryEntry(koreanTerm = "검수", englishTerm = "Review")

                // when: dictionary 액션 요청
                val result = simulator.sendMessage(
                    fromUser = null,
                    action = "dictionary",
                    actionData = mapOf("text" to "이 문서는 번역과 검수 과정을 거쳤습니다.")
                )

                // then: 찾은 단어 2개 반환
                val response = result.response.contentAsString
                assert(response.contains("번역 가이드라인에서 찾은 단어: 2개")) { "찾은 단어 수가 올바르지 않습니다" }
                assert(response.contains("번역 → Translation")) { "번역 단어가 포함되어야 합니다" }
                assert(response.contains("검수 → Review")) { "검수 단어가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 텍스트에서 사전 단어를 찾지 못하면 안내 메시지를 반환한다")
            fun `텍스트에서 사전 단어를 찾지 못하면 안내 메시지를 반환한다`() {
                // given: 사전에 단어 등록
                helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")

                // when: 관련 없는 텍스트로 dictionary 액션 요청
                val result = simulator.sendMessage(
                    fromUser = null,
                    action = "dictionary",
                    actionData = mapOf("text" to "이 문서는 아무 관련이 없습니다.")
                )

                // then: 찾지 못했다는 메시지
                val response = result.response.contentAsString
                assert(response.contains("번역 가이드라인에서 찾은 단어: 0개")) { "찾은 단어 수가 0이어야 합니다" }
                assert(response.contains("찾지 못했습니다")) { "찾지 못했다는 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 텍스트 길이 정보가 올바르게 표시된다")
            fun `텍스트 길이 정보가 올바르게 표시된다`() {
                // given: 공백이 포함된 텍스트
                val text = "번역 작업"

                // when: dictionary 액션 요청
                val result = simulator.sendMessage(
                    fromUser = null,
                    action = "dictionary",
                    actionData = mapOf("text" to text)
                )

                // then: 텍스트 길이 정보 확인
                val response = result.response.contentAsString
                assert(response.contains("텍스트의 길이(전체): 5자")) { "전체 길이가 올바르지 않습니다" }
                assert(response.contains("텍스트의 길이(공백/개행 등 제외): 4자")) { "공백 제외 길이가 올바르지 않습니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 긴 텍스트는 잘려서 표시된다")
            fun `긴 텍스트는 잘려서 표시된다`() {
                // given: 50자 초과 텍스트
                val longText = "a".repeat(100)

                // when: dictionary 액션 요청
                val result = simulator.sendMessage(
                    fromUser = null,
                    action = "dictionary",
                    actionData = mapOf("text" to longText)
                )

                // then: 텍스트가 잘려서 표시
                val response = result.response.contentAsString
                assert(response.contains("...")) { "긴 텍스트가 잘려야 합니다" }
            }
        }

        @Nested
        @DisplayName("gettoken 액션")
        inner class GetToken {

            @Test
            @DisplayName("POST /api/kakao/skill - 등록된 사용자가 gettoken 요청")
            fun `등록된 사용자가 요청하면 토큰을 반환한다`() {
                // given: 등록된 사용자
                val user = helper.createActiveStudent("2024001", "홍길동")
                val expectedToken = user.auth.secretToken.toString()

                // when: gettoken 요청
                val result = simulator.sendMessage(fromUser = user, action = "gettoken")

                // then: 토큰이 포함된 응답
                val response = result.response.contentAsString
                assert(response.contains("로그인이 가능합니다")) { "로그인 안내 메시지가 포함되어야 합니다" }
                assert(response.contains(expectedToken)) { "토큰이 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 미등록 사용자가 gettoken 요청")
            fun `미등록 사용자가 요청하면 오류 메시지를 반환한다`() {
                // when: 익명 사용자의 gettoken 요청
                val result = simulator.sendMessage(fromUser = null, action = "gettoken")

                // then: 오류 메시지
                val response = result.response.contentAsString
                assert(response.contains("등록된 사용자만")) { "등록된 사용자만 이용 가능하다는 메시지가 포함되어야 합니다" }
            }
        }

        @Nested
        @DisplayName("retoken 액션")
        inner class RegenerateToken {

            @Test
            @DisplayName("POST /api/kakao/skill - 확인 메시지와 함께 retoken 요청")
            fun `확인 메시지와 함께 요청하면 토큰을 재발급한다`() {
                // given: 등록된 사용자
                val user = helper.createActiveStudent("2024001", "홍길동")
                val originalToken = user.auth.secretToken.toString()

                // when: 확인 메시지와 함께 retoken 요청
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "retoken",
                    actionData = mapOf("sure" to "예, 재발급합니다.")
                )

                // then: 재발급된 토큰 반환
                val response = result.response.contentAsString
                assert(response.contains("재발급되었습니다")) { "재발급 완료 메시지가 포함되어야 합니다" }
                assert(!response.contains(originalToken)) { "기존 토큰과 다른 새 토큰이어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 확인 메시지 없이 retoken 요청")
            fun `확인 메시지 없이 요청하면 취소 메시지를 반환한다`() {
                // given: 등록된 사용자
                val user = helper.createActiveStudent("2024002", "김철수")

                // when: 확인 메시지 없이 retoken 요청
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "retoken",
                    actionData = mapOf("sure" to "아니오")
                )

                // then: 취소 메시지
                val response = result.response.contentAsString
                assert(response.contains("취소했습니다")) { "취소 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 미등록 사용자가 retoken 요청")
            fun `미등록 사용자가 요청하면 오류 메시지를 반환한다`() {
                // when: 익명 사용자의 retoken 요청
                val result = simulator.sendMessage(
                    fromUser = null,
                    action = "retoken",
                    actionData = mapOf("sure" to "예, 재발급합니다.")
                )

                // then: 오류 메시지
                val response = result.response.contentAsString
                assert(response.contains("등록된 사용자만")) { "등록된 사용자만 이용 가능하다는 메시지가 포함되어야 합니다" }
            }
        }
    }

    // ==================== 사용자 정보 스킬 ====================

    @Nested
    @DisplayName("사용자 정보 스킬")
    inner class UserInfoSkills {

        @BeforeEach
        fun setUpPhase() {
            helper.setupScenario(SystemPhase.RECRUITMENT)
            helper.clearGlossary()
        }

        @Nested
        @DisplayName("myinfo 액션")
        inner class MyInfo {

            @Test
            @DisplayName("POST /api/kakao/skill - 승인된 학생이 myinfo 요청")
            fun `승인된 학생이 요청하면 자신의 정보를 반환한다`() {
                // given: 승인된 학생
                val user = helper.createActiveStudent("2024001", "홍길동")

                // when: myinfo 요청
                val result = simulator.sendMessage(fromUser = user, action = "myinfo")

                // then: 사용자 정보 반환
                val response = result.response.contentAsString
                assert(response.contains("2024001")) { "학번이 포함되어야 합니다" }
                assert(response.contains("홍길동")) { "이름이 포함되어야 합니다" }
                assert(response.contains("번역버디")) { "학생은 번역버디로 표시되어야 합니다" }
                assert(response.contains("번역한 글자 수")) { "글자 수 정보가 포함되어야 합니다" }
                assert(response.contains("봉사 시간")) { "봉사 시간 정보가 포함되어야 합니다" }
                assert(response.contains("받은 경고")) { "경고 정보가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 관리자가 myinfo 요청")
            fun `관리자가 요청하면 관리자 정보를 반환한다`() {
                // given: 관리자
                val admin = helper.createAdminAndGetToken("admin", "관리자").user

                // when: myinfo 요청
                val result = simulator.sendMessage(fromUser = admin, action = "myinfo")

                // then: 관리자 정보 반환
                val response = result.response.contentAsString
                assert(response.contains("관리자")) { "관리자로 표시되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 가입 요청 심사 중인 사용자가 myinfo 요청")
            fun `가입 요청 심사 중인 사용자는 심사 중 메시지를 받는다`() {
                // given: 가입 요청 생성
                val plusfriendUserKey = "pending-user-key"
                createRegistration("25-001", "대기자", plusfriendUserKey)

                // when: myinfo 요청
                val result = simulator.sendMessageFromAnonymous(
                    plusfriendUserKey = plusfriendUserKey,
                    action = "myinfo"
                )

                // then: 심사 중 메시지
                val response = result.response.contentAsString
                assert(response.contains("심사 중")) { "심사 중 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 미가입자가 myinfo 요청")
            fun `미가입자는 가입 요청 안내를 받는다`() {
                // when: 미가입자의 myinfo 요청
                val result = simulator.sendMessageFromAnonymous(
                    plusfriendUserKey = "unknown-user-key",
                    action = "myinfo"
                )

                // then: 미가입 안내
                val response = result.response.contentAsString
                assert(response.contains("가입 요청을 하지 않았습니다")) { "미가입 안내 메시지가 포함되어야 합니다" }
            }
        }
    }

    // 헬퍼 메서드: Skill을 통해 회원가입 신청 생성
    private fun createRegistration(studentNumber: String, studentName: String, plusfriendUserKey: String) {
        simulator.sendMessageFromAnonymous(
            plusfriendUserKey = plusfriendUserKey,
            action = "registration_apply",
            actionData = mapOf(
                "studentNumber" to studentNumber,
                "studentName" to studentName
            )
        )
    }

    // ==================== 과제 관련 스킬 ====================

    @Nested
    @DisplayName("내 과제 관련 스킬")
    inner class MyTaskSkills {

        @BeforeEach
        fun setUpPhase() {
            helper.setupScenario(SystemPhase.TRANSLATION)
        }

        @Nested
        @DisplayName("givenwork 액션 - 미완료 과제 없음")
        inner class NoIncompleteTasks {

            @Test
            @DisplayName("POST /api/kakao/skill - 미완료 과제가 없으면 안내 메시지를 반환한다")
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
            @DisplayName("POST /api/kakao/skill - 모든 과제가 완료된 경우 안내 메시지를 반환한다")
            fun `모든 과제가 완료된 경우 안내 메시지를 반환한다`() {
                // given: 완료된 과제만 있는 학생
                val student = helper.createActiveStudent("25-002", "김철수")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "완료된 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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
        @DisplayName("givenwork 액션 - 미완료 과제 있음")
        inner class HasIncompleteTasks {

            @Test
            @DisplayName("POST /api/kakao/skill - 미완료 과제가 있으면 목록을 반환한다")
            fun `미완료 과제가 있으면 목록을 반환한다`() {
                // given: 미완료 과제가 있는 학생
                val student = helper.createActiveStudent("25-003", "박영희")
                helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "가온누리 공지사항 번역",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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
            @DisplayName("POST /api/kakao/skill - 과제 목록은 오래된 순으로 정렬된다")
            fun `과제 목록은 오래된 순으로 정렬된다`() {
                // given: 여러 미완료 과제가 있는 학생
                val student = helper.createActiveStudent("25-004", "이민수")
                val oldTask = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "오래된 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
                )
                helper.setTaskAssignedAt(oldTask.id, Instant.now().minusSeconds(24 * 60 * 60)) // 1일 전

                val newTask = helper.createTranslationTask(
                    TranslationTask.TaskType.EXTERNAL_POST,
                    "새로운 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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
            @DisplayName("POST /api/kakao/skill - 외부 과제 유형이 올바르게 표시된다")
            fun `외부 과제 유형이 올바르게 표시된다`() {
                // given: 외부 과제가 있는 학생
                val student = helper.createActiveStudent("25-005", "정우성")
                helper.createTranslationTask(
                    TranslationTask.TaskType.EXTERNAL_POST,
                    "외부 문서 번역",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
                )

                // when: givenwork 요청
                val result = simulator.sendMessage(fromUser = student, action = "givenwork")

                // then: 외부 유형 표시
                val response = result.response.contentAsString
                assert(response.contains("외부")) { "외부 과제 유형이 포함되어야 합니다" }
                assert(response.contains("외부 문서 번역")) { "과제명이 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 경과 시간이 올바르게 표시된다")
            fun `경과 시간이 올바르게 표시된다`() {
                // given: 2시간 전에 할당된 과제
                val student = helper.createActiveStudent("25-006", "한지민")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "시간 테스트 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
                )
                helper.setTaskAssignedAt(task.id, Instant.now().minusSeconds(2 * 60 * 60)) // 2시간 전

                // when: givenwork 요청
                val result = simulator.sendMessage(fromUser = student, action = "givenwork")

                // then: 경과 시간 표시
                val response = result.response.contentAsString
                assert(response.contains("2시간")) { "경과 시간이 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 빠른 응답에 홈 메뉴와 끝난 번역 보고하기가 포함된다")
            fun `빠른 응답에 홈 메뉴와 끝난 번역 보고하기가 포함된다`() {
                // given: 미완료 과제가 있는 학생
                val student = helper.createActiveStudent("25-007", "강호동")
                helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "테스트 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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
        @DisplayName("givenwork 액션 - 지각된 과제")
        inner class LateTasks {

            @Test
            @DisplayName("POST /api/kakao/skill - 지각된 과제는 지각 경고 메시지를 포함한다")
            fun `지각된 과제는 지각 경고 메시지를 포함한다`() {
                // given: 49시간 전에 할당된 미완료 과제 (48시간 임계값 초과)
                val student = helper.createActiveStudent("25-008", "지각학생")
                val lateTask = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "지각된 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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
            @DisplayName("POST /api/kakao/skill - 정상 과제와 지각 과제가 함께 있으면 각각 다르게 표시된다")
            fun `정상 과제와 지각 과제가 함께 있으면 각각 다르게 표시된다`() {
                // given: 정상 과제와 지각 과제가 함께 있는 학생
                val student = helper.createActiveStudent("25-009", "복합학생")

                val normalTask = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "정상 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
                )
                helper.setTaskAssignedAt(normalTask.id, Instant.now().minusSeconds(1 * 60 * 60)) // 1시간 전

                val lateTask = helper.createTranslationTask(
                    TranslationTask.TaskType.EXTERNAL_POST,
                    "지각 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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
            @DisplayName("POST /api/kakao/skill - 여러 개의 지각 과제가 있어도 모두 경고 메시지가 포함된다")
            fun `여러 개의 지각 과제가 있어도 모두 경고 메시지가 포함된다`() {
                // given: 여러 지각 과제가 있는 학생
                val student = helper.createActiveStudent("25-010", "다중지각학생")

                val lateTask1 = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "지각 과제 1",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
                )
                helper.setTaskAssignedAt(lateTask1.id, Instant.now().minusSeconds(72 * 60 * 60)) // 72시간 전

                val lateTask2 = helper.createTranslationTask(
                    TranslationTask.TaskType.EXTERNAL_POST,
                    "지각 과제 2",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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
        @DisplayName("givenwork 액션 - 미인증 사용자")
        inner class UnauthenticatedUser {

            @Test
            @DisplayName("POST /api/kakao/skill - 미인증 사용자가 givenwork 요청")
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
        @DisplayName("givenwork 액션 - 경과 시간 포맷팅")
        inner class ElapsedTimeFormatting {

            @Test
            @DisplayName("POST /api/kakao/skill - 방금 할당된 과제는 방금으로 표시된다")
            fun `방금 할당된 과제는 방금으로 표시된다`() {
                // given: 방금 할당된 과제
                val student = helper.createActiveStudent("25-011", "신규학생")
                helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "방금 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
                )

                // when: givenwork 요청
                val result = simulator.sendMessage(fromUser = student, action = "givenwork")

                // then: 방금 표시
                val response = result.response.contentAsString
                assert(response.contains("방금")) { "방금 할당된 과제는 '방금'으로 표시되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 1일 이상 경과한 과제는 분을 생략한다")
            fun `1일 이상 경과한 과제는 분을 생략한다`() {
                // given: 1일 2시간 전에 할당된 과제
                val student = helper.createActiveStudent("25-012", "장기학생")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "장기 과제",
                    student,
                    TranslationTask.AssignmentType.AUTOMATIC,
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

    // ==================== 과제 완료 보고 스킬 ====================

    @Nested
    @DisplayName("과제 완료 보고 스킬")
    inner class TaskCompletionReportSkills {

        @BeforeEach
        fun setUpPhase() {
            helper.setupScenario(SystemPhase.TRANSLATION)
        }

        @Nested
        @DisplayName("정상적인 완료 보고")
        inner class ReportCompletionSuccess {

            @Test
            @DisplayName("POST /api/kakao/skill - 정상적으로 과제를 완료 보고하면 성공 메시지를 반환한다")
            fun `정상적으로 과제를 완료 보고하면 성공 메시지를 반환한다`() {
                // given: 활성 학생과 할당된 과제
                val user = helper.createActiveStudent("25-001", "홍길동")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "테스트 과제",
                    user
                )

                // when: 과제 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "1500"
                    )
                )

                // then: 성공 메시지 반환
                val response = result.response.contentAsString
                assert(response.contains("번역 보고가 완료되었습니다")) { "성공 메시지가 포함되어야 합니다" }
                assert(response.contains("1500자")) { "총 글자수가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 글자 수에 자가 포함되어도 정상 처리된다")
            fun `글자 수에 자가 포함되어도 정상 처리된다`() {
                // given: 활성 학생과 할당된 과제
                val user = helper.createActiveStudent("25-002", "김철수")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "테스트 과제",
                    user
                )

                // when: 글자 수에 "자" 접미사가 포함된 상태로 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "2000자"
                    )
                )

                // then: 정상 처리
                val response = result.response.contentAsString
                assert(response.contains("번역 보고가 완료되었습니다")) { "성공 메시지가 포함되어야 합니다" }
                assert(response.contains("2000자")) { "총 글자수가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 글자 수에 공백과 자가 포함되어도 정상 처리된다")
            fun `글자 수에 공백과 자가 포함되어도 정상 처리된다`() {
                // given: 활성 학생과 할당된 과제
                val user = helper.createActiveStudent("25-003", "박영희")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "테스트 과제",
                    user
                )

                // when: 글자 수에 공백과 "자" 접미사가 포함된 상태로 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to " 3000 자 "
                    )
                )

                // then: 정상 처리
                val response = result.response.contentAsString
                assert(response.contains("번역 보고가 완료되었습니다")) { "성공 메시지가 포함되어야 합니다" }
                assert(response.contains("3000자")) { "총 글자수가 포함되어야 합니다" }
            }
        }

        @Nested
        @DisplayName("지각 완료 보고")
        inner class LateCompletionReport {

            @Test
            @DisplayName("POST /api/kakao/skill - 지각 완료 시 사용자에게 경고 개수가 표시된다")
            fun `지각 완료 시 사용자에게 경고 개수가 표시된다`() {
                // given: 활성 학생과 49시간 전에 할당된 과제
                val user = helper.createActiveStudent("25-010", "지각사용자")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "지각 과제",
                    user
                )
                // 49시간 전으로 할당 시간 설정
                val oldAssignedAt = Instant.now().minusSeconds(49 * 60 * 60)
                helper.setTaskAssignedAt(task.id, oldAssignedAt)

                // when: 과제 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "1000"
                    )
                )

                // then: 경고 안내 메시지 포함
                val response = result.response.contentAsString
                assert(response.contains("경고 안내")) { "경고 안내가 포함되어야 합니다" }
                assert(response.contains("늦게 제출")) { "지각 사유가 포함되어야 합니다" }
                assert(response.contains("경고 1회가 등록")) { "경고 등록 안내가 포함되어야 합니다" }
            }
        }

        @Nested
        @DisplayName("오류 케이스")
        inner class ErrorCases {

            @Test
            @DisplayName("POST /api/kakao/skill - 다른 사용자의 과제명이면 에러 메시지를 반환한다")
            fun `다른 사용자의 과제명이면 에러 메시지를 반환한다`() {
                // given: 활성 학생
                val user = helper.createActiveStudent("25-020", "사용자")

                // when: 존재하지 않는 과제명으로 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to "존재하지 않는 과제명입니다",
                        "characterCount" to "1000"
                    )
                )

                // then: 에러 메시지 반환
                val response = result.response.contentAsString
                assert(response.contains("존재하지 않는 과제")) { "존재하지 않는 과제 에러 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 이미 완료된 과제면 에러 메시지를 반환한다")
            fun `이미 완료된 과제면 에러 메시지를 반환한다`() {
                // given: 활성 학생과 이미 완료된 과제
                val user = helper.createActiveStudent("25-021", "완료사용자")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "이미 완료된 과제",
                    user
                )

                // 먼저 과제 완료
                simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "1000"
                    )
                )

                // when: 이미 완료된 과제에 다시 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "2000"
                    )
                )

                // then: 에러 메시지 반환
                val response = result.response.contentAsString
                assert(response.contains("이미 완료된 과제")) { "이미 완료된 과제 에러 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 글자 수가 음수면 에러 메시지를 반환한다")
            fun `글자 수가 음수면 에러 메시지를 반환한다`() {
                // given: 활성 학생과 할당된 과제
                val user = helper.createActiveStudent("25-022", "음수사용자")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "음수 테스트 과제",
                    user
                )

                // when: 음수 글자 수로 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "-100"
                    )
                )

                // then: 에러 메시지 반환
                val response = result.response.contentAsString
                assert(response.contains("0 이상이어야 합니다")) { "글자 수 음수 에러 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 글자 수가 숫자가 아니면 에러 메시지를 반환한다")
            fun `글자 수가 숫자가 아니면 에러 메시지를 반환한다`() {
                // given: 활성 학생과 할당된 과제
                val user = helper.createActiveStudent("25-023", "비숫자사용자")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "비숫자 테스트 과제",
                    user
                )

                // when: 숫자가 아닌 글자 수로 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "천오백"
                    )
                )

                // then: 에러 메시지 반환
                val response = result.response.contentAsString
                assert(response.contains("숫자여야 합니다")) { "글자 수 숫자 에러 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 존재하지 않는 과제명이면 에러 메시지를 반환한다")
            fun `존재하지 않는 과제명이면 에러 메시지를 반환한다`() {
                // given: 활성 학생
                val user = helper.createActiveStudent("25-024", "잘못된과제사용자")

                // when: 존재하지 않는 과제명으로 완료 보고
                val result = simulator.sendMessage(
                    fromUser = user,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to "존재하지 않는 과제명입니다",
                        "characterCount" to "1000"
                    )
                )

                // then: 에러 메시지 반환
                val response = result.response.contentAsString
                assert(response.contains("존재하지 않는 과제")) { "존재하지 않는 과제 에러 메시지가 포함되어야 합니다" }
            }

            @Test
            @DisplayName("POST /api/kakao/skill - 미가입 사용자가 reportwork 요청")
            fun `미가입 사용자가 요청하면 가입 안내 메시지를 반환한다`() {
                // given: 과제 생성 (다른 사용자)
                val taskOwner = helper.createActiveStudent("25-025", "과제소유자")
                val task = helper.createTranslationTask(
                    TranslationTask.TaskType.GAONNURI_POST,
                    "테스트 과제",
                    taskOwner
                )

                // when: 미가입 사용자가 완료 보고
                val result = simulator.sendMessage(
                    fromUser = null,
                    action = "reportwork",
                    actionData = mapOf(
                        "taskDescription" to task.taskDescription,
                        "characterCount" to "1000"
                    )
                )

                // then: 가입 안내 메시지 반환
                val response = result.response.contentAsString
                assert(response.contains("가입된 번역버디만")) { "가입 안내 메시지가 포함되어야 합니다" }
            }
        }
    }
}

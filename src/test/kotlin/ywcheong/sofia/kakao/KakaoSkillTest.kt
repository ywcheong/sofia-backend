package ywcheong.sofia.kakao

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase

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
        helper.setupScenario(SystemPhase.RECRUITMENT)
        helper.clearGlossary()
        simulator = FakeKakaoMessageSimulator(
            mockMvc = mockMvc,
            objectMapper = objectMapper,
            testScenarioHelper = helper
        )
    }

    @Nested
    @DisplayName("healthcheck 액션")
    inner class HealthCheck {

        @Test
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

    @Nested
    @DisplayName("myinfo 액션")
    inner class MyInfo {

        @Test
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
}

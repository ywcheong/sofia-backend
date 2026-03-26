package ywcheong.sofia.config.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.get
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("보안")
class SecurityTest(
    private val mockMvc: MockMvc,
    private val helper: TestScenarioHelper,
) {

    @BeforeEach
    fun setUp() {
        helper.setupScenario(SystemPhase.RECRUITMENT)
    }

    @Nested
    @DisplayName("GET /test/security/public - 공개 API")
    inner class PublicApiTests {

        @Test
        @DisplayName("GET /test/security/public - 인증 없이 접근 가능")
        fun `인증 없이 접근 가능하다`() {
            mockMvc.get("/test/security/public")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("public") }
                }
        }
    }

    @Nested
    @DisplayName("GET /test/security/user - 사용자 API")
    inner class UserApiTests {

        @Test
        @DisplayName("GET /test/security/user - 유효한 User 토큰으로 접근 성공")
        fun `유효한 User 토큰으로 접근 성공`() {
            // given - STUDENT 유저 생성
            val studentInfo = helper.createStudentAndGetToken("25-001", "홍길동")

            // when & then
            mockMvc.get("/test/security/user") {
                header("Authorization", helper.adminAuthHeader(studentInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("user") }
                jsonPath("$.studentNumber") { value("25-001") }
            }
        }

        @Test
        @DisplayName("GET /test/security/user - 잘못된 토큰 형식으로 접근 실패")
        fun `잘못된 토큰 형식으로 접근 실패`() {
            mockMvc.get("/test/security/user") {
                header("Authorization", "user not-a-uuid")
            }.andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        @DisplayName("GET /test/security/user - 존재하지 않는 토큰으로 접근 시 인증 안됨")
        fun `존재하지 않는 토큰으로 접근 시 인증 안됨`() {
            val randomToken = UUID.randomUUID()

            mockMvc.get("/test/security/user") {
                header("Authorization", "user $randomToken")
            }.andExpect {
                // 존재하지 않는 토큰이므로 인증 실패
                status { isUnauthorized() }
            }
        }

        @Test
        @DisplayName("GET /test/security/user - 토큰 없이 접근 시 실패")
        fun `토큰 없이 접근 시 실패`() {
            mockMvc.get("/test/security/user")
                .andExpect {
                    // 인증하지 않아서 권한이 부족
                    status { isForbidden() }
                }
        }
    }

    @Nested
    @DisplayName("GET /test/security/admin - 관리자 API")
    inner class AdminApiTests {

        @Test
        @DisplayName("GET /test/security/admin - ADMIN 권한으로 접근 성공")
        fun `ADMIN 권한으로 접근 성공`() {
            // given - ADMIN 유저 생성
            val adminInfo = helper.createAdminAndGetToken()

            // when & then
            mockMvc.get("/test/security/admin") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("admin") }
            }
        }

        @Test
        @DisplayName("GET /test/security/admin - STUDENT 권한으로 접근 실패")
        fun `STUDENT 권한으로 접근 실패`() {
            // given - STUDENT 유저 생성
            val studentInfo = helper.createStudentAndGetToken("25-002", "학생")

            // when & then
            mockMvc.get("/test/security/admin") {
                header("Authorization", helper.adminAuthHeader(studentInfo.secretToken))
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        @DisplayName("GET /test/security/admin - 인증 없이 접근 실패")
        fun `인증 없이 접근 실패`() {
            mockMvc.get("/test/security/admin")
                .andExpect {
                    // 인증하지 않아서 권한이 부족
                    status { isForbidden() }
                }
        }
    }

    @Nested
    @DisplayName("GET /test/security/kakao - 카카오 API")
    inner class KakaoApiTests {

        @Test
        @DisplayName("GET /test/security/kakao - 유효한 Kakao 토큰으로 접근 성공")
        fun `유효한 Kakao 토큰으로 접근 성공`() {
            mockMvc.get("/test/security/kakao") {
                header("Authorization", helper.kakaoAuthHeader())
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("kakao") }
            }
        }

        @Test
        @DisplayName("GET /test/security/kakao - 잘못된 Kakao 토큰으로 접근 실패")
        fun `잘못된 Kakao 토큰으로 접근 실패`() {
            mockMvc.get("/test/security/kakao") {
                header("Authorization", "kakao wrong-token")
            }.andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        @DisplayName("GET /test/security/kakao - 인증 없이 접근 실패")
        fun `인증 없이 접근 실패`() {
            mockMvc.get("/test/security/kakao")
                .andExpect {
                    // 인증하지 않아서 권한이 부족
                    status { isForbidden() }
                }
        }
    }

    @Nested
    @DisplayName("Authorization 헤더 형식 테스트")
    inner class AuthorizationHeaderTests {

        @Test
        @DisplayName("GET /test/security/public - 지원되지 않는 인증 유형으로 401 반환")
        fun `지원되지 않는 인증 유형은 401 반환`() {
            mockMvc.get("/test/security/public") {
                header("Authorization", "bearer some-token")
            }.andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        @DisplayName("GET /test/security/user - User 토큰 앞의 공백 무시")
        fun `User 토큰 앞의 공백은 무시됨`() {
            // given
            val studentInfo = helper.createStudentAndGetToken("25-003", "테스트")

            // when & then - 공백이 포함되어도 정상 동작
            mockMvc.get("/test/security/user") {
                header("Authorization", "user  ${studentInfo.secretToken}")  // 공백 2개
            }.andExpect {
                status { isOk() }
            }
        }
    }
}

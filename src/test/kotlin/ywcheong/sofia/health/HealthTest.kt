package ywcheong.sofia.health

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("헬스체크")
class HealthTest(
    private val mockMvc: MockMvc,
    private val helper: TestScenarioHelper,
) {
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun setUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)
    }

    @Nested
    @DisplayName("GET /health - 서버 상태 확인")
    inner class HealthCheck {

        @Test
        fun `인증 없이도 서버 상태를 확인할 수 있다`() {
            mockMvc.get("/health").andExpect {
                status { isOk() }
                content { string("ok") }
            }
        }
    }

    @Nested
    @DisplayName("GET /auth/check - 사용자 식별 확인")
    inner class IdentityCheck {

        @Test
        fun `인증된 사용자면 200과 사용자 정보를 반환한다`() {
            mockMvc.get("/auth/check") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(adminInfo.userId.toString()) }
                jsonPath("$.userStudentNumber") { value("admin") }
                jsonPath("$.userStudentName") { value("관리자") }
            }
        }

        @Test
        fun `인증되지 않았으면 201을 반환한다`() {
            mockMvc.get("/auth/check").andExpect {
                status { isCreated() }
            }
        }
    }
}

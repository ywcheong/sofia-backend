package ywcheong.sofia.phase

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.kakao.FakeKakaoMessageSimulator
import ywcheong.sofia.task.TranslationTask

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("시스템 페이즈")
class SystemPhaseTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var simulator: FakeKakaoMessageSimulator
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun cleanUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.RECRUITMENT)
        simulator = FakeKakaoMessageSimulator(
            mockMvc = mockMvc,
            objectMapper = objectMapper,
            testScenarioHelper = helper
        )
    }

    @Nested
    @DisplayName("GET /system-phase - 현재 페이즈 조회")
    inner class GetCurrentPhase {

        @Test
        fun `현재 페이즈를 조회하면 200과 현재 및 다음 페이즈 정보를 반환한다`() {
            // when & then
            mockMvc.get("/system-phase") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("RECRUITMENT") }
                jsonPath("$.currentPhaseDisplayName") { value("모집") }
                jsonPath("$.nextPhase") { value("TRANSLATION") }
                jsonPath("$.nextPhaseDisplayName") { value("번역") }
            }
        }
    }

    // === Availability Check Tests ===

    @Nested
    @DisplayName("GET /system-phase/transit/recruitment/availability - RECRUITMENT 전환 가능 여부")
    inner class CheckRecruitmentAvailability {

        @Test
        fun `RECRUITMENT 전환 가능 여부를 조회하면 항상 available true를 반환한다`() {
            // when & then
            mockMvc.get("/system-phase/transit/recruitment/availability") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(true) }
            }
        }
    }

    @Nested
    @DisplayName("GET /system-phase/transit/translation/availability - TRANSLATION 전환 가능 여부")
    inner class CheckTranslationAvailability {

        @Test
        fun `대기 신청이 없으면 available true를 반환한다`() {
            // when & then
            mockMvc.get("/system-phase/transit/translation/availability") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(true) }
                jsonPath("$.pendingRegistrations") { isEmpty() }
            }
        }

        @Test
        fun `대기 신청이 있으면 available false와 신청자 정보를 반환한다`() {
            // given - 대기 신청 생성
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = "test-pending-user-1",
                action = "registration_apply",
                actionData = mapOf("studentNumber" to "25-001", "studentName" to "김대기")
            )

            // when & then
            mockMvc.get("/system-phase/transit/translation/availability") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(false) }
                jsonPath("$.pendingRegistrations") { isNotEmpty() }
                jsonPath("$.pendingRegistrations[0].studentNumber") { value("25-001") }
                jsonPath("$.pendingRegistrations[0].studentName") { value("김대기") }
            }
        }
    }

    @Nested
    @DisplayName("GET /system-phase/transit/settlement/availability - SETTLEMENT 전환 가능 여부")
    inner class CheckSettlementAvailability {

        @Test
        fun `대기 신청과 미완료 과제가 없으면 available true를 반환한다`() {
            // given - TRANSLATION 페이즈로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)

            // when & then
            mockMvc.get("/system-phase/transit/settlement/availability") {
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(true) }
                jsonPath("$.pendingRegistrations") { isEmpty() }
                jsonPath("$.incompleteTasks") { isEmpty() }
            }
        }

        @Test
        fun `미완료 과제가 있으면 available false와 과제 목록을 반환한다`() {
            // given - TRANSLATION 페이즈로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)
            val student = helper.createActiveStudent("2024001", "홍길동")
            helper.createTranslationTask(
                TranslationTask.TaskType.GAONNURI_POST,
                "테스트 과제",
                student
            )

            // when & then
            mockMvc.get("/system-phase/transit/settlement/availability") {
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(false) }
                jsonPath("$.incompleteTasks") { isNotEmpty() }
                jsonPath("$.incompleteTasks[0].taskType") { value("GAONNURI_POST") }
                jsonPath("$.incompleteTasks[0].description") { value("테스트 과제") }
                jsonPath("$.incompleteTasks[0].assigneeName") { value("홍길동") }
            }
        }

        @Test
        fun `대기 신청이 있으면 pendingRegistrations에 신청자 정보를 포함한다`() {
            // given - TRANSLATION 페이즈로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)
            simulator.sendMessageFromAnonymous(
                plusfriendUserKey = "test-settlement-pending-1",
                action = "registration_apply",
                actionData = mapOf("studentNumber" to "25-002", "studentName" to "박대기")
            )

            // when & then
            mockMvc.get("/system-phase/transit/settlement/availability") {
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(false) }
                jsonPath("$.pendingRegistrations") { isNotEmpty() }
                jsonPath("$.pendingRegistrations[0].studentNumber") { value("25-002") }
                jsonPath("$.pendingRegistrations[0].studentName") { value("박대기") }
            }
        }
    }

    @Nested
    @DisplayName("GET /system-phase/transit/deactivation/availability - DEACTIVATION 전환 가능 여부")
    inner class CheckDeactivationAvailability {

        @Test
        fun `DEACTIVATION 전환 가능 여부를 조회하면 항상 available true를 반환한다`() {
            // when & then
            mockMvc.get("/system-phase/transit/deactivation/availability") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(true) }
            }
        }
    }

    // === Phase Transit Tests ===

    @Nested
    @DisplayName("POST /system-phase/transit/recruitment - RECRUITMENT 전환")
    inner class TransitToRecruitment {

        @Test
        fun `DEACTIVATION에서 RECRUITMENT로 전환하면 200을 반환한다`() {
            // given - DEACTIVATION 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.DEACTIVATION)

            // when & then
            mockMvc.post("/system-phase/transit/recruitment") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("RECRUITMENT") }
                jsonPath("$.currentPhaseDisplayName") { value("모집") }
            }
        }
    }

    @Nested
    @DisplayName("POST /system-phase/transit/translation - TRANSLATION 전환")
    inner class TransitToTranslation {

        @Test
        fun `RECRUITMENT에서 TRANSLATION으로 전환하면 200을 반환한다`() {
            // when & then
            mockMvc.post("/system-phase/transit/translation") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("TRANSLATION") }
                jsonPath("$.currentPhaseDisplayName") { value("번역") }
            }
        }

        @Test
        fun `잘못된 페이즈에서 TRANSLATION으로 전환하면 400을 반환한다`() {
            // given - DEACTIVATION 상태로 설정 (잘못된 시작 페이즈)
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.DEACTIVATION)

            // when & then
            mockMvc.post("/system-phase/transit/translation") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `이미 TRANSLATION인 상태에서 다시 전환하면 400을 반환한다`() {
            // given - TRANSLATION 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)

            // when & then
            mockMvc.post("/system-phase/transit/translation") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /system-phase/transit/settlement - SETTLEMENT 전환")
    inner class TransitToSettlement {

        @Test
        fun `TRANSLATION에서 SETTLEMENT로 전환하면 200을 반환한다`() {
            // given - TRANSLATION 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.TRANSLATION)

            // when & then
            mockMvc.post("/system-phase/transit/settlement") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("SETTLEMENT") }
                jsonPath("$.currentPhaseDisplayName") { value("정산") }
            }
        }

        @Test
        fun `잘못된 페이즈에서 SETTLEMENT로 전환하면 400을 반환한다`() {
            // given - RECRUITMENT 상태 (잘못된 시작 페이즈)

            // when & then
            mockMvc.post("/system-phase/transit/settlement") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /system-phase/transit/deactivation - DEACTIVATION 전환")
    inner class TransitToDeactivation {

        @Test
        fun `SETTLEMENT에서 DEACTIVATION으로 전환하면 200을 반환한다`() {
            // given - SETTLEMENT 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.SETTLEMENT)
            val request = mapOf("userRetentionMode" to "KEEP_ALL")

            // when & then
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("DEACTIVATION") }
                jsonPath("$.currentPhaseDisplayName") { value("비활성") }
            }
        }

        @Test
        fun `잘못된 페이즈에서 DEACTIVATION으로 전환하면 400을 반환한다`() {
            // given - RECRUITMENT 상태 (잘못된 시작 페이즈)
            val request = mapOf("userRetentionMode" to "KEEP_ALL")

            // when & then
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `KEEP_ADMINS 모드로 전환하면 200을 반환한다`() {
            // given - SETTLEMENT 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.SETTLEMENT)
            val request = mapOf("userRetentionMode" to "KEEP_ADMINS")

            // when & then
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("DEACTIVATION") }
            }
        }

        @Test
        fun `KEEP_SELF 모드로 전환하면 200을 반환한다`() {
            // given - SETTLEMENT 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.SETTLEMENT)
            val request = mapOf("userRetentionMode" to "KEEP_SELF")

            // when & then
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("DEACTIVATION") }
            }
        }

        @Test
        fun `KEEP_ALL 모드로 전환하면 모든 사용자가 유지된다`() {
            // given - SETTLEMENT 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.SETTLEMENT)
            // 사용자 생성
            helper.createActiveStudent("25-001", "학생1")
            helper.createActiveStudent("25-002", "학생2")
            helper.createAdminAndGetToken("admin2", "관리자2")

            val request = mapOf("userRetentionMode" to "KEEP_ALL")

            // when - DEACTIVATION 전환
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            helper.setPhase(SystemPhase.RECRUITMENT)

            // then - 모든 사용자가 유지됨을 검증
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(4) } // newAdminInfo + student1 + student2 + otherAdmin
            }
        }

        @Test
        fun `KEEP_ADMINS 모드로 전환하면 학생만 삭제되고 관리자는 유지된다`() {
            // given - SETTLEMENT 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.SETTLEMENT)
            // 학생 2명 + 관리자 1명 생성
            helper.createActiveStudent("25-010", "학생A")
            helper.createActiveStudent("25-011", "학생B")
            helper.createAdminAndGetToken("admin-keep", "유지될관리자")

            val request = mapOf("userRetentionMode" to "KEEP_ADMINS")

            // when - DEACTIVATION 전환
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            helper.setPhase(SystemPhase.RECRUITMENT)

            // then - 관리자만 유지됨을 검증
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) } // newAdminInfo + otherAdmin
                jsonPath("$.content[*].role") { value(mutableListOf("ADMIN", "ADMIN")) }
            }
        }

        @Test
        fun `KEEP_SELF 모드로 전환하면 요청한 관리자만 유지된다`() {
            // given - SETTLEMENT 상태로 설정
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.SETTLEMENT)
            // 학생 2명 + 다른 관리자 1명 생성
            helper.createActiveStudent("25-020", "삭제될학생1")
            helper.createActiveStudent("25-021", "삭제될학생2")
            helper.createAdminAndGetToken("admin-delete", "삭제될관리자")

            val request = mapOf("userRetentionMode" to "KEEP_SELF")

            // when - DEACTIVATION 전환
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }

            helper.setPhase(SystemPhase.RECRUITMENT)

            // then - 요청한 관리자만 유지됨을 검증
            mockMvc.get("/users?page=0&size=10") {
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) } // newAdminInfo만 유지
                jsonPath("$.content[0].id") { value(newAdminInfo.userId.toString()) }
            }
        }
    }

    @Nested
    @DisplayName("전체 페이즈 사이클")
    open inner class FullPhaseCycle {

        @Test
        @Transactional
        open fun `전체 페이즈 사이클을 순차적으로 전환할 수 있다`() {
            // given - DEACTIVATION 상태에서 시작
            val newAdminInfo = helper.setupScenarioWithAdmin(SystemPhase.DEACTIVATION)

            // when & then - DEACTIVATION -> RECRUITMENT
            mockMvc.post("/system-phase/transit/recruitment") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("RECRUITMENT") }
                jsonPath("$.currentPhaseDisplayName") { value("모집") }
            }

            // when & then - RECRUITMENT -> TRANSLATION
            mockMvc.post("/system-phase/transit/translation") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("TRANSLATION") }
                jsonPath("$.currentPhaseDisplayName") { value("번역") }
            }

            // when & then - TRANSLATION -> SETTLEMENT
            mockMvc.post("/system-phase/transit/settlement") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("SETTLEMENT") }
                jsonPath("$.currentPhaseDisplayName") { value("정산") }
            }

            // when & then - SETTLEMENT -> DEACTIVATION
            val deactivationRequest = mapOf("userRetentionMode" to "KEEP_ALL")
            mockMvc.post("/system-phase/transit/deactivation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(deactivationRequest)
                header("Authorization", helper.adminAuthHeader(newAdminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.currentPhase") { value("DEACTIVATION") }
                jsonPath("$.currentPhaseDisplayName") { value("비활성") }
            }
        }
    }
}

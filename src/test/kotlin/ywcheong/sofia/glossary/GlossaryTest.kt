package ywcheong.sofia.glossary

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.phase.SystemPhase

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("용어집")
class GlossaryTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper,
) {
    private lateinit var adminInfo: TestScenarioHelper.AdminAuthInfo

    @BeforeEach
    fun setUp() {
        adminInfo = helper.setupScenarioWithAdmin(SystemPhase.RECRUITMENT)
        helper.clearGlossary()
    }

    @Nested
    @DisplayName("GET /glossary - 사전 항목 조회")
    inner class SearchGlossary {

        @Test
        @DisplayName("GET /glossary - 전체 사전 항목 조회")
        fun `키워드 없이 전체 사전을 조회하면 모든 항목이 반환된다`() {
            // given: 사전에 2개의 항목이 등록되어 있음
            helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")
            helper.createGlossaryEntry(koreanTerm = "검수", englishTerm = "Review")

            // when & then: 전체 조회 시 2개 항목 반환
            mockMvc.get("/glossary")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(2) }
                    jsonPath("$[0].koreanTerm") { value("번역") }
                    jsonPath("$[1].koreanTerm") { value("검수") }
                }
        }

        @Test
        @DisplayName("GET /glossary - 한국어 키워드로 사전 검색")
        fun `한국어 키워드로 검색하면 매칭되는 항목만 반환된다`() {
            // given: 사전에 항목들이 등록되어 있음
            helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")
            helper.createGlossaryEntry(koreanTerm = "검수", englishTerm = "Review")
            helper.createGlossaryEntry(koreanTerm = "교정", englishTerm = "Proofreading")

            // when & then: "번" 키워드로 검색
            mockMvc.get("/glossary?keyword=번")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].koreanTerm") { value("번역") }
                }
        }

        @Test
        @DisplayName("GET /glossary - 영어 키워드 검색 불가 확인")
        fun `영어 키워드로 검색해도 항목은 매칭하지 않는다`() {
            // given
            helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")
            helper.createGlossaryEntry(koreanTerm = "검수", englishTerm = "Review")

            // when & then: "Rev" 키워드로 검색
            mockMvc.get("/glossary?keyword=Rev")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(0) }
                }
        }

        @Test
        @DisplayName("GET /glossary - 검색 결과 없음 시 빈 배열 반환")
        fun `매칭되는 항목이 없으면 빈 배열을 반환한다`() {
            // given: 사전에 항목이 있음
            helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")

            // when & then: 존재하지 않는 키워드로 검색
            mockMvc.get("/glossary?keyword=없는용어")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(0) }
                }
        }

        @Test
        @DisplayName("GET /glossary - 빈 사전 조회 시 빈 배열 반환")
        fun `사전이 비어있으면 빈 배열을 반환한다`() {
            // when & then: 빈 사전 조회
            mockMvc.get("/glossary")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(0) }
                }
        }
    }

    @Nested
    @DisplayName("POST /glossary - 사전 항목 추가")
    inner class AddGlossaryEntry {

        @Test
        @DisplayName("POST /glossary - 새 사전 항목 추가")
        fun `새로운 사전 항목을 추가하면 200과 함께 항목이 반환된다`() {
            // given: 추가할 항목
            val request = mapOf(
                "koreanTerm" to "용어",
                "englishTerm" to "Term"
            )

            // when & then: 항목 추가 (관리자 권한 필요)
            mockMvc.post("/glossary") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.koreanTerm") { value("용어") }
                jsonPath("$.englishTerm") { value("Term") }
                jsonPath("$.id") { exists() }
            }
        }

        @Test
        @DisplayName("POST /glossary - 한국어 용어 길이 초과 시 400 반환")
        fun `한국어 용어가 200자를 초과하면 400을 반환한다`() {
            // given: 200자 초과 한국어 용어
            val request = mapOf(
                "koreanTerm" to "a".repeat(201),
                "englishTerm" to "Term"
            )

            // when & then: 400 에러
            mockMvc.post("/glossary") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        @DisplayName("POST /glossary - 영어 용어 길이 초과 시 400 반환")
        fun `영어 용어가 200자를 초과하면 400을 반환한다`() {
            // given: 200자 초과 영어 용어
            val request = mapOf(
                "koreanTerm" to "용어",
                "englishTerm" to "a".repeat(201)
            )

            // when & then: 400 에러
            mockMvc.post("/glossary") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("PUT /glossary/{id} - 사전 항목 수정")
    inner class UpdateGlossaryEntry {

        @Test
        @DisplayName("PUT /glossary/{id} - 사전 항목 수정")
        fun `기존 사전 항목을 수정하면 변경된 내용이 반환된다`() {
            // given: 기존 항목
            val entry = helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")
            val request = mapOf(
                "koreanTerm" to "번역(수정)",
                "englishTerm" to "Translation (Modified)"
            )

            // when & then: 항목 수정 (관리자 권한 필요)
            mockMvc.put("/glossary/${entry.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
                jsonPath("$.koreanTerm") { value("번역(수정)") }
                jsonPath("$.englishTerm") { value("Translation (Modified)") }
                jsonPath("$.id") { value(entry.id.toString()) }
            }
        }

        @Test
        @DisplayName("PUT /glossary/{id} - 존재하지 않는 항목 수정 시 400 반환")
        fun `존재하지 않는 항목을 수정하면 400을 반환한다`() {
            // given: 존재하지 않는 ID
            val request = mapOf(
                "koreanTerm" to "용어",
                "englishTerm" to "Term"
            )

            // when & then: 400 에러
            mockMvc.put("/glossary/00000000-0000-0000-0000-000000000000") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("DELETE /glossary/{id} - 사전 항목 삭제")
    inner class DeleteGlossaryEntry {

        @Test
        @DisplayName("DELETE /glossary/{id} - 사전 항목 삭제")
        fun `사전 항목을 삭제하면 200을 반환한다`() {
            // given: 기존 항목
            val entry = helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")

            // when & then: 항목 삭제 (관리자 권한 필요)
            mockMvc.delete("/glossary/${entry.id}") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("DELETE /glossary/{id} - 존재하지 않는 항목 삭제 시 400 반환")
        fun `존재하지 않는 항목을 삭제하면 400을 반환한다`() {
            // when & then: 존재하지 않는 ID로 삭제 시도
            mockMvc.delete("/glossary/00000000-0000-0000-0000-000000000000") {
                header("Authorization", helper.adminAuthHeader(adminInfo.secretToken))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    @DisplayName("POST /glossary/auto-map - 사전 자동 매핑")
    inner class AutoMapGlossary {

        @Test
        @DisplayName("POST /glossary/auto-map - 텍스트 내 한국어 용어 자동 매핑")
        fun `텍스트에 포함된 한국어 용어들이 자동 매핑된다`() {
            // given: 사전에 항목들이 등록되어 있음
            helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")
            helper.createGlossaryEntry(koreanTerm = "검수", englishTerm = "Review")
            helper.createGlossaryEntry(koreanTerm = "미포함", englishTerm = "NotIncluded")

            val request = mapOf(
                "text" to "이 문서는 번역과 검수 과정을 거쳤습니다."
            )

            // when & then: 자동 매핑
            mockMvc.post("/glossary/auto-map") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
        }

        @Test
        @DisplayName("POST /glossary/auto-map - 매칭 용어 없음 시 빈 배열 반환")
        fun `매칭되는 용어가 없으면 빈 배열을 반환한다`() {
            // given: 사전에 항목이 있지만 텍스트에는 포함되지 않음
            helper.createGlossaryEntry(koreanTerm = "번역", englishTerm = "Translation")

            val request = mapOf(
                "text" to "이 문서는 아무 관련이 없습니다."
            )

            // when & then: 빈 결과
            mockMvc.post("/glossary/auto-map") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
        }

        @Test
        @DisplayName("POST /glossary/auto-map - 공백 차이 허용 매핑")
        fun `공백이 다른 용어도 매핑된다`() {
            // given: "우리 학교"로 등록된 항목
            helper.createGlossaryEntry(koreanTerm = "우리 학교", englishTerm = "Our School")

            // when: "우리학교" (공백 없이)로 검색
            val request = mapOf(
                "text" to "우리학교에 갔습니다."
            )

            // then: 매핑 성공, 원본 표시용 용어 반환
            mockMvc.post("/glossary/auto-map") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].koreanTerm") { value("우리 학교") }
                jsonPath("$[0].englishTerm") { value("Our School") }
            }
        }

        @Test
        @DisplayName("POST /glossary/auto-map - 텍스트 공백 허용 매핑")
        fun `텍스트에 공백이 있어도 매핑된다`() {
            // given: "우리학교"로 등록된 항목
            helper.createGlossaryEntry(koreanTerm = "우리학교", englishTerm = "Our School")

            // when: "우리 학교" (공백 있이) 텍스트로 검색
            val request = mapOf(
                "text" to "우리 학교에 갔습니다."
            )

            // then: 매핑 성공
            mockMvc.post("/glossary/auto-map") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].koreanTerm") { value("우리학교") }
            }
        }

        @Test
        @DisplayName("POST /glossary/auto-map - 대소문자 무시 매핑")
        fun `대소문자가 달라도 매핑된다`() {
            // given: 영문이 섞인 용어
            helper.createGlossaryEntry(koreanTerm = "API 서버", englishTerm = "API Server")

            // when: 소문자로 검색
            val request = mapOf(
                "text" to "api 서버를 구축했습니다."
            )

            // then: 매핑 성공, 원본 표시용 용어 반환
            mockMvc.post("/glossary/auto-map") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].koreanTerm") { value("API 서버") }
            }
        }
    }
}

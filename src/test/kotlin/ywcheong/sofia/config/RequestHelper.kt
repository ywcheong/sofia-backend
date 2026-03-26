package ywcheong.sofia.config

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import tools.jackson.core.type.TypeReference
import java.util.*

/**
 * MockMvc 요청을 간소화하는 헬퍼 클래스.
 *
 * HTTP 요청, 인증 헤더 추가, JSON 직렬화/역직렬화를 간편하게 처리합니다.
 *
 * ## 사용 예시
 * ```kotlin
 * val requestHelper = RequestHelper(mockMvc, objectMapper, helper)
 *
 * // GET 요청
 * val result = requestHelper.get("/users?page=0&size=10", adminInfo.secretToken)
 *
 * // POST 요청
 * val createResult = requestHelper.post("/tasks", requestBody, adminInfo.secretToken)
 *
 * // 응답 추출
 * val response: MyResponse = requestHelper.extractResponse(result)
 * ```
 */
class RequestHelper(
    private val mockMvc: MockMvc,
    val objectMapper: ObjectMapper,
    private val helper: TestScenarioHelper
) {

    // ==================== HTTP 요청 메서드 ====================

    /**
     * GET 요청을 수행합니다.
     *
     * @param endpoint 요청할 엔드포인트 (쿼리 파라미터 포함 가능)
     * @param token 관리자 인증 토큰 (선택사항)
     * @return MvcResult
     */
    fun get(endpoint: String, token: UUID? = null): MvcResult {
        return mockMvc.get(endpoint) {
            token?.let { header("Authorization", helper.adminAuthHeader(it)) }
        }.andReturn()
    }

    /**
     * POST 요청을 수행합니다.
     *
     * @param endpoint 요청할 엔드포인트
     * @param body 요청 본문 (JSON으로 직렬화됨)
     * @param token 관리자 인증 토큰 (선택사항)
     * @return MvcResult
     */
    fun post(endpoint: String, body: Any? = null, token: UUID? = null): MvcResult {
        return mockMvc.post(endpoint) {
            contentType = MediaType.APPLICATION_JSON
            body?.let { content = objectMapper.writeValueAsString(it) }
            token?.let { header("Authorization", helper.adminAuthHeader(it)) }
        }.andReturn()
    }

    /**
     * PUT 요청을 수행합니다.
     *
     * @param endpoint 요청할 엔드포인트
     * @param body 요청 본문 (JSON으로 직렬화됨)
     * @param token 관리자 인증 토큰 (선택사항)
     * @return MvcResult
     */
    fun put(endpoint: String, body: Any? = null, token: UUID? = null): MvcResult {
        return mockMvc.put(endpoint) {
            contentType = MediaType.APPLICATION_JSON
            body?.let { content = objectMapper.writeValueAsString(it) }
            token?.let { header("Authorization", helper.adminAuthHeader(it)) }
        }.andReturn()
    }

    /**
     * PATCH 요청을 수행합니다.
     *
     * @param endpoint 요청할 엔드포인트
     * @param body 요청 본문 (JSON으로 직렬화됨)
     * @param token 관리자 인증 토큰 (선택사항)
     * @return MvcResult
     */
    fun patch(endpoint: String, body: Any? = null, token: UUID? = null): MvcResult {
        return mockMvc.patch(endpoint) {
            contentType = MediaType.APPLICATION_JSON
            body?.let { content = objectMapper.writeValueAsString(it) }
            token?.let { header("Authorization", helper.adminAuthHeader(it)) }
        }.andReturn()
    }

    /**
     * DELETE 요청을 수행합니다.
     *
     * @param endpoint 요청할 엔드포인트
     * @param token 관리자 인증 토큰 (선택사항)
     * @return MvcResult
     */
    fun delete(endpoint: String, token: UUID? = null): MvcResult {
        return mockMvc.delete(endpoint) {
            token?.let { header("Authorization", helper.adminAuthHeader(it)) }
        }.andReturn()
    }

    // ==================== 카카오 인증 요청 메서드 ====================

    /**
     * 카카오 인증으로 POST 요청을 수행합니다.
     *
     * @param endpoint 요청할 엔드포인트
     * @param body 요청 본문 (JSON으로 직렬화됨)
     * @param kakaoSecret 카카오 시크릿 (기본값: DEFAULT_KAKAO_SECRET)
     * @return MvcResult
     */
    fun postWithKakao(
        endpoint: String,
        body: Any? = null,
        kakaoSecret: String = TestScenarioHelper.DEFAULT_KAKAO_SECRET
    ): MvcResult {
        return mockMvc.post(endpoint) {
            contentType = MediaType.APPLICATION_JSON
            body?.let { content = objectMapper.writeValueAsString(it) }
            header("Authorization", helper.kakaoAuthHeader(kakaoSecret))
        }.andReturn()
    }

    // ==================== 인증 헤더 생성 ====================

    /**
     * 관리자 인증 헤더 값을 생성합니다.
     *
     * @param token 관리자 토큰
     * @return "user {token}" 형식의 인증 헤더 값
     */
    fun adminAuth(token: UUID): String = helper.adminAuthHeader(token)

    /**
     * 카카오 인증 헤더 값을 생성합니다.
     *
     * @param secret 카카오 시크릿 (기본값: DEFAULT_KAKAO_SECRET)
     * @return "kakao {secret}" 형식의 인증 헤더 값
     */
    fun kakaoAuth(secret: String = TestScenarioHelper.DEFAULT_KAKAO_SECRET): String =
        helper.kakaoAuthHeader(secret)

    // ==================== 응답 검증 및 추출 ====================

    /**
     * HTTP 상태 코드를 검증합니다.
     *
     * @param result MvcResult
     * @param expectedStatus 기대하는 상태 코드
     * @throws AssertionError 상태 코드가 일치하지 않을 경우
     */
    fun assertStatus(result: MvcResult, expectedStatus: Int) {
        val actualStatus = result.response.status
        check(actualStatus == expectedStatus) {
            "Expected status $expectedStatus but was $actualStatus. Response: ${result.response.contentAsString}"
        }
    }

    /**
     * 응답이 200 OK인지 검증합니다.
     */
    fun assertOk(result: MvcResult) = assertStatus(result, 200)

    /**
     * 응답이 201 Created인지 검증합니다.
     */
    fun assertCreated(result: MvcResult) = assertStatus(result, 201)

    /**
     * 응답이 204 No Content인지 검증합니다.
     */
    fun assertNoContent(result: MvcResult) = assertStatus(result, 204)

    /**
     * 응답이 400 Bad Request인지 검증합니다.
     */
    fun assertBadRequest(result: MvcResult) = assertStatus(result, 400)

    /**
     * 응답이 401 Unauthorized인지 검증합니다.
     */
    fun assertUnauthorized(result: MvcResult) = assertStatus(result, 401)

    /**
     * 응답이 403 Forbidden인지 검증합니다.
     */
    fun assertForbidden(result: MvcResult) = assertStatus(result, 403)

    /**
     * 응답이 404 Not Found인지 검증합니다.
     */
    fun assertNotFound(result: MvcResult) = assertStatus(result, 404)

    /**
     * JSON 응답을 지정된 타입으로 역직렬화합니다.
     *
     * @param result MvcResult
     * @return 역직렬화된 객체
     */
    inline fun <reified T> extractResponse(result: MvcResult): T {
        val content = result.response.contentAsString
        return objectMapper.readValue(content, object : TypeReference<T>() {})
    }

    /**
     * JSON 응답을 JsonNode로 반환합니다.
     *
     * @param result MvcResult
     * @return JsonNode
     */
    fun extractJsonNode(result: MvcResult): tools.jackson.databind.JsonNode {
        val content = result.response.contentAsString
        return objectMapper.readTree(content)
    }

    /**
     * JSON 응답에서 특정 경로의 값을 추출합니다.
     *
     * @param result MvcResult
     * @param path JSON 경로 (예: "data.userId")
     * @return 경로에 해당하는 텍스트 값
     */
    fun extractPath(result: MvcResult, path: String): String? {
        val node = extractJsonNode(result)
        val pathParts = path.split(".")
        var currentNode = node

        for (part in pathParts) {
            if (currentNode.has(part)) {
                currentNode = currentNode.get(part)
            } else {
                return null
            }
        }

        return if (currentNode.isNull) null else currentNode.asText()
    }

    /**
     * JSON 응답에서 UUID를 추출합니다.
     *
     * @param result MvcResult
     * @param path JSON 경로
     * @return UUID
     */
    fun extractUuid(result: MvcResult, path: String): UUID? {
        val value = extractPath(result, path) ?: return null
        return UUID.fromString(value)
    }

    /**
     * 응답 본문을 문자열로 반환합니다.
     *
     * @param result MvcResult
     * @return 응답 본문 문자열
     */
    fun extractContentAsString(result: MvcResult): String {
        return result.response.contentAsString
    }
}

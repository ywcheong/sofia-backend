package ywcheong.sofia.kakao

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.config.TestScenarioHelper
import ywcheong.sofia.user.SofiaUser
import java.util.*

class FakeKakaoMessageSimulator(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val testScenarioHelper: TestScenarioHelper,
) {
    /**
     * 카카오 스킬 서버에서 온 요청을 시뮬레이션합니다.
     *
     * @param fromUser 메시지를 보낸 사용자 (null이면 랜덤 plusfriendUserKey 생성)
     * @param action 실행할 액션 이름
     * @param actionData 액션에 전달할 파라미터들
     */
    fun sendMessage(
        fromUser: SofiaUser?,
        action: String,
        actionData: Map<String, String> = emptyMap(),
    ): MvcResult {
        // fromUser가 null이면 랜덤 plusfriendUserKey 생성 (실제 카카오에서는 항상 유효한 값 제공)
        val plusfriendUserKey = fromUser?.auth?.plusfriendUserKey ?: "anonymous-${UUID.randomUUID()}"
        val requestBody = buildRequestBody(
            plusfriendUserKey = plusfriendUserKey,
            action = action,
            actionData = actionData,
        )

        return mockMvc.perform(
            post(ENDPOINT)
                .header("Authorization", testScenarioHelper.kakaoAuthHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
        )
            .andExpect(status().isOk)
            .andReturn()
    }

    /**
     * 익명 사용자(등록된 사용자가 아님)가 특정 plusfriendUserKey로 요청을 시뮬레이션합니다.
     * 가입 요청 중인 사용자를 테스트할 때 사용합니다.
     *
     * @param plusfriendUserKey 카카오톡 사용자 키
     * @param action 실행할 액션 이름
     * @param actionData 액션에 전달할 파라미터들
     */
    fun sendMessageFromAnonymous(
        plusfriendUserKey: String,
        action: String,
        actionData: Map<String, String> = emptyMap(),
    ): MvcResult {
        val requestBody = buildRequestBody(
            plusfriendUserKey = plusfriendUserKey,
            action = action,
            actionData = actionData,
        )

        return mockMvc.perform(
            post(ENDPOINT)
                .header("Authorization", testScenarioHelper.kakaoAuthHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
        )
            .andExpect(status().isOk)
            .andReturn()
    }

    private fun buildRequestBody(
        plusfriendUserKey: String?,
        action: String,
        actionData: Map<String, String>,
    ): Request {
        val detailParams = buildMap {
            put("action", Request.Action.DetailParam(origin = action, value = action, groupName = ""))
            actionData.forEach { (key, value) ->
                put(key, Request.Action.DetailParam(origin = value, value = value, groupName = ""))
            }
        }

        return Request(
            action = Request.Action(detailParams = detailParams),
            userRequest = Request.UserRequest(
                user = Request.UserRequest.User(
                    properties = Request.UserRequest.User.Properties(plusfriendUserKey = plusfriendUserKey)
                )
            )
        )
    }

    private data class Request(
        val action: Action,
        val userRequest: UserRequest,
    ) {
        data class Action(val detailParams: Map<String, DetailParam>) {
            data class DetailParam(val origin: String, val value: String, val groupName: String)
        }

        data class UserRequest(val user: User) {
            data class User(val properties: Properties) {
                data class Properties(val plusfriendUserKey: String?)
            }
        }
    }

    private companion object {
        const val ENDPOINT = "/api/kakao/skill"
    }
}

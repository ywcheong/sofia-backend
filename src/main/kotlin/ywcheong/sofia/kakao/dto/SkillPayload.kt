package ywcheong.sofia.kakao.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SkillPayload(
    val userRequest: UserRequest? = null,
    val action: Action? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserRequest(
    val utterance: String? = null,
    val user: User? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class User(
    val id: String? = null,
    val properties: UserProperties? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserProperties(
    val plusfriendUserKey: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Action(
    val id: String? = null,
    val name: String? = null,
    val params: Map<String, Any?> = emptyMap(),
    val detailParams: Map<String, Any?> = emptyMap(),
)

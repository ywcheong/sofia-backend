package ywcheong.sofia.kakao

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.phase.SystemPhase
import ywcheong.sofia.phase.SystemPhaseService
import ywcheong.sofia.user.SofiaUser
import kotlin.reflect.KClass

abstract class KakaoSkill<ACT : Any>(
    val responsibleAction: String,
    private val actionDataType: KClass<out ACT>,
    private val allowedPhases: List<SystemPhase>,
) {
    protected val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var systemPhaseService: SystemPhaseService

    data class KakaoSkillResult(
        val message: String,
        val quickReplies: List<QuickReply> = emptyList(),
    ) {
        data class QuickReply(
            val label: String,
            val messageText: String,
            val action: String = "message",
        )
    }

    /**
     * KakaoSkillController에서 호출하는 진입점.
     * Map<String, String> 파라미터를 ACT 타입으로 변환 후 handleInternal에 위임한다.
     * allowedPhases가 설정된 경우 SystemPhaseService를 통해 phase 검증을 수행한다.
     */
    fun handle(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: Map<String, String>,
    ): KakaoSkillResult {
        val boundParams = bindParams(params)

        return systemPhaseService.executeIfPhase(allowedPhases) {
            handleInternal(user, plusFriendUserKey, boundParams)
        }
    }

    /**
     * Map<String, String>을 ACT 타입으로 변환한다.
     * Jackson ObjectMapper를 사용하여 JSON 경유 변환을 수행한다.
     */
    private fun bindParams(params: Map<String, String>): ACT {
        val json = objectMapper.writeValueAsString(params)
        return objectMapper.readValue(json, actionDataType.java)
    }

    /**
     * 구현체에서 비즈니스 로직을 구현하는 추상 메서드.
     */
    protected abstract fun handleInternal(
        user: SofiaUser?,
        plusFriendUserKey: String,
        params: ACT,
    ): KakaoSkillResult
}

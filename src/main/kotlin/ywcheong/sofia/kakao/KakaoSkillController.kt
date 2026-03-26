package ywcheong.sofia.kakao

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.kakao.KakaoSkillException.Companion.fromBusinessException
import ywcheong.sofia.kakao.KakaoSkillException.Companion.fromSystemError
import ywcheong.sofia.user.SofiaUser
import ywcheong.sofia.user.SofiaUserRepository
import ywcheong.sofia.user.auth.SofiaPermission
import ywcheong.sofia.user.auth.SofiaUserAuthRepository

@RestController
@RequestMapping("/api/kakao/skill")
@Tag(name = "Kakao Skill", description = "카카오톡 챗봇 스킬 webhook API")
class KakaoSkillController(
    skills: List<KakaoSkill<*>>,
    private val userAuthRepository: SofiaUserAuthRepository,
    private val userRepository: SofiaUserRepository,
) {
    private val logger = KotlinLogging.logger {}
    private val skillMap: Map<String, KakaoSkill<*>> = skills.associateBy { it.responsibleAction }

    data class SkillRequest(
        val action: Action,
        val userRequest: UserRequest,
    ) {
        data class Action(
            val detailParams: Map<String, DetailParam>,
        ) {
            data class DetailParam(
                val origin: String,
                val value: String,
                val groupName: String,
            )
        }

        data class UserRequest(
            val user: User,
        ) {
            data class User(
                val properties: Properties,
            ) {
                data class Properties(
                    val plusfriendUserKey: String?,
                )
            }
        }
    }

    data class SkillResponse(
        val version: String = "2.0",
        val template: Template,
    ) {
        data class Template(
            val outputs: List<Output>,
            val quickReplies: List<QuickReply> = emptyList(),
        )

        data class Output(
            val simpleText: SimpleText,
        ) {
            data class SimpleText(
                val text: String,
            )
        }

        data class QuickReply(
            val label: String,
            val action: String = "message",
            val messageText: String,
        )
    }

    @PostMapping
    @AvailableCondition(phases = [], permissions = [SofiaPermission.KAKAO_ENDPOINT]) // 페이즈는 각 스킬마다 별도 검증
    @Operation(summary = "카카오톡 챗봇 스킬 처리", description = "action 파라미터로 적절한 스킬로 분기")
    fun handleSkill(@RequestBody request: SkillRequest): SkillResponse {
        logger.info { "Received Kakao Skill request: $request" }

        return try {
            val actionName = extractActionName(request)
            val plusFriendUserKey = extractPlusFriendUserKey(request)
            val params = extractParams(request)

            val skill = skillMap[actionName]
                ?: throw IllegalStateException("No KakaoSkill found for action: $actionName")

            val user = findUser(plusFriendUserKey)

            val result = skill.handle(
                user = user,
                plusFriendUserKey = plusFriendUserKey ?: "",
                params = params,
            )

            convertToResponse(result)
        } catch (ex: BusinessException) {
            throw fromBusinessException(ex)
        } catch (ex: Exception) {
            throw fromSystemError(ex)
        }
    }

    private fun extractActionName(request: SkillRequest): String {
        val actionParam = request.action.detailParams["action"]
        return checkNotNull(actionParam) { "action.detailParams.action is required" }.origin
    }

    private fun extractPlusFriendUserKey(request: SkillRequest): String? {
        return request.userRequest.user.properties.plusfriendUserKey
    }

    private fun extractParams(request: SkillRequest): Map<String, String> {
        return request.action.detailParams
            .filterKeys { it != "action" }
            .mapValues { (_, param) -> param.origin }
    }

    private fun findUser(plusfriendUserKey: String?): SofiaUser? {
        if (plusfriendUserKey.isNullOrBlank()) return null
        val userAuth = userAuthRepository.findByPlusfriendUserKey(plusfriendUserKey).orElse(null)
            ?: return null
        return userRepository.findById(userAuth.user.id).orElse(null)
    }

    private fun convertToResponse(result: KakaoSkill.KakaoSkillResult): SkillResponse {
        val outputs = listOf(
            SkillResponse.Output(
                simpleText = SkillResponse.Output.SimpleText(text = result.message) // 현재는 이미지 사용계획 없음
            )
        )
        val quickReplies = result.quickReplies.map { qr ->
            SkillResponse.QuickReply(
                label = qr.label,
                action = "message", // 단순 발화처리만 사용
                messageText = qr.messageText,
            )
        }
        return SkillResponse(
            template = SkillResponse.Template(
                outputs = outputs,
                quickReplies = quickReplies,
            )
        )
    }
}

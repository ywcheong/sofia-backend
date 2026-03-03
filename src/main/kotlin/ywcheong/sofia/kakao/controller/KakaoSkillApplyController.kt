package ywcheong.sofia.kakao.controller

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.kakao.dto.SkillPayload
import ywcheong.sofia.kakao.dto.SkillResponse

@RestController
@RequestMapping("/kakao/skill")
class KakaoSkillApplyController {
    @PostMapping("/apply")
    fun apply(
        @RequestBody payload: SkillPayload,
    ): SkillResponse {
        val utterance = payload.userRequest?.utterance?.takeIf { it.isNotBlank() } ?: "요청이 접수되었습니다."
        return SkillResponse.simpleText("apply received: $utterance")
    }
}

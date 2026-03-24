package ywcheong.sofia.user.auth

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.config.security.CurrentUser
import ywcheong.sofia.user.SofiaUser

@RestController
@Tag(name = "Auth", description = "사용자 인증 상태 확인 API")
class SofiaAuthController {
    @AvailableCondition(phases = [], permissions = [])
    @GetMapping("/auth/check")
    fun identityCheck(@CurrentUser user: SofiaUser?): ResponseEntity<String> {
        return if (user != null) {
            ResponseEntity.ok(user.id.toString())
        } else {
            ResponseEntity.status(HttpStatus.CREATED).build()
        }
    }
}
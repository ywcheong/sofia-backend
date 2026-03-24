package ywcheong.sofia.health

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.config.security.CurrentUser
import ywcheong.sofia.user.SofiaUser

@RestController
@Tag(name = "Health", description = "서버 상태 확인 API")
class HealthController {
    @AvailableCondition(phases = [], permissions = [])
    @GetMapping("/health")
    fun health(): String = "ok"
}

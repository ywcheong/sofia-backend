package ywcheong.sofia.email

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ywcheong.sofia.aspect.AvailableCondition
import ywcheong.sofia.commons.BusinessException
import ywcheong.sofia.email.user.SofiaUserEmailRepository
import java.util.*

@RestController
@RequestMapping("/api/email/subscription")
@Tag(name = "Email Subscription", description = "이메일 수신 설정 API")
class EmailSubscriptionController(
    private val userEmailRepository: SofiaUserEmailRepository,
) {
    @GetMapping("/{token}/status")
    @AvailableCondition(phases = [], permissions = [])
    @Operation(summary = "이메일 수신 상태 조회", description = "토큰으로 이메일 수신 상태를 조회합니다.")
    fun getSubscriptionStatus(@PathVariable token: String): SubscriptionStatusResponse {
        val uuidToken = UUID.fromString(token)
        val userEmail = userEmailRepository.findByUnsubscribeToken(uuidToken)
            ?: throw BusinessException("유효하지 않은 토큰입니다.")

        return SubscriptionStatusResponse(
            email = userEmail.email,
            subscribed = !userEmail.isUnsubscribed,
        )
    }

    @PutMapping("/{token}/subscribe")
    @AvailableCondition(phases = [], permissions = [])
    @Operation(summary = "이메일 수신 허용", description = "이메일 수신을 허용합니다.")
    fun subscribe(@PathVariable token: String): SubscriptionStatusResponse {
        val uuidToken = UUID.fromString(token)
        val userEmail = userEmailRepository.findByUnsubscribeToken(uuidToken)
            ?: throw BusinessException("유효하지 않은 토큰입니다.")

        userEmail.subscribe()
        userEmailRepository.save(userEmail)

        return SubscriptionStatusResponse(
            email = userEmail.email,
            subscribed = true,
        )
    }

    @PutMapping("/{token}/unsubscribe")
    @AvailableCondition(phases = [], permissions = [])
    @Operation(summary = "이메일 수신 거부", description = "이메일 수신을 거부합니다.")
    fun unsubscribe(@PathVariable token: String): SubscriptionStatusResponse {
        val uuidToken = UUID.fromString(token)
        val userEmail = userEmailRepository.findByUnsubscribeToken(uuidToken)
            ?: throw BusinessException("유효하지 않은 토큰입니다.")

        userEmail.unsubscribe()
        userEmailRepository.save(userEmail)

        return SubscriptionStatusResponse(
            email = userEmail.email,
            subscribed = false,
        )
    }

    data class SubscriptionStatusResponse(
        val email: String,
        val subscribed: Boolean,
    )
}

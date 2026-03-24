package ywcheong.sofia.user.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ywcheong.sofia.user.SofiaUser

@Service
class SofiaUserAuthService(
    private val userAuthRepository: SofiaUserAuthRepository,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun regenerateToken(user: SofiaUser): String {
        return user.auth.regenerateSecretToken().toString()
    }

    companion object {
        const val MAX_REGENERATION_ATTEMPTS = 100
    }
}

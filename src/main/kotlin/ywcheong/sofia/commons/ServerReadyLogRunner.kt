package ywcheong.sofia.commons

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class ServerReadyLogRunner : CommandLineRunner {
    private val logger = KotlinLogging.logger { }

    override fun run(vararg args: String) {
        logger.info { "서버가 준비되었습니다." }
    }
}
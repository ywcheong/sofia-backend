package ywcheong.sofia.config.security

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

@Component
class RequestLoggingFilter : OncePerRequestFilter() {

    private val logger = KotlinLogging.logger {}

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = UUID.randomUUID().toString()
        MDC.put("traceId", traceId)
        request.setAttribute("startTime", System.currentTimeMillis())

        try {
            logger.info { "[요청] ${request.method} ${request.requestURI}" }
            filterChain.doFilter(request, response)
        } finally {
            val status = response.status
            val elapsed = (request.getAttribute("startTime") as? Long)?.let { System.currentTimeMillis() - it }
            logger.info {
                buildString {
                    append("[응답] ${request.method} ${request.requestURI} -> $status")
                    elapsed?.also { append(" (${elapsed}ms)") }
                }
            }
            MDC.clear()
        }
    }
}

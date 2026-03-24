package ywcheong.sofia.config.mvc

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.databind.ObjectMapper
import ywcheong.sofia.commons.BusinessException
import java.net.URI

@RestControllerAdvice
@Tag(name = "Global Exception Handler", description = "전역 예외 처리기")
class GlobalExceptionHandler(
    private val swaggerUiConfig: SwaggerUiConfigProperties,
) : ResponseEntityExceptionHandler() {
    private val log = KotlinLogging.logger {}

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ProblemDetail {
        log.warn { "비즈니스 오류: ${ex.message}" }

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message).apply {
            type = swaggerUiConfigUri
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ProblemDetail {
        log.error(ex) { "장애 발생: ${ex.message}" }

        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.").apply {
            type = swaggerUiConfigUri
        }
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ProblemDetail {
        log.warn { "권한 오류: ${ex.message}" }

        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "권한이 부족합니다.").apply {
            type = swaggerUiConfigUri
        }
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ProblemDetail {
        log.warn { "인증 오류: ${ex.message}" }

        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "인증에 실패했습니다.").apply {
            type = swaggerUiConfigUri
        }
    }

    private val swaggerUiConfigUri: URI
        get() = URI.create("${swaggerUiConfig.path}/index.html")

    @Component
    class SecurityExceptionHandler(
        private val globalExceptionHandler: GlobalExceptionHandler,
        private val objectMapper: ObjectMapper,
    ) : AuthenticationEntryPoint {
        override fun commence(
            request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException
        ) {
            val problemDetail = globalExceptionHandler.handleAuthenticationException(authException)
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.outputStream.write(objectMapper.writeValueAsBytes(problemDetail))
        }
    }
}

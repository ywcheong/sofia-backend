package ywcheong.sofia.config.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import ywcheong.sofia.config.mvc.GlobalExceptionHandler

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfiguration(
    private val requestLoggingFilter: RequestLoggingFilter,
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    private val securityExceptionHandler: GlobalExceptionHandler.SecurityExceptionHandler,
    private val corsProperties: CorsProperties,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .exceptionHandling { it.authenticationEntryPoint(securityExceptionHandler) }
            .headers { it.frameOptions { frameOptions -> frameOptions.sameOrigin() } }
            .addFilterBefore(
                requestLoggingFilter,
                ExceptionTranslationFilter::class.java,
            )
            .addFilterAfter(
                apiKeyAuthFilter,
                ExceptionTranslationFilter::class.java,
            )
            .build()
    }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("*")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}

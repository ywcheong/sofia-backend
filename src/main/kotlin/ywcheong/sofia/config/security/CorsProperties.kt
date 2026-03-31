package ywcheong.sofia.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sofia.cors")
data class CorsProperties(
    val allowedOrigins: List<String>,
)

package ywcheong.sofia.email

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sofia.email")
data class EmailProperties(
    val enabled: Boolean,
    val fromEmail: String,
    val fromName: String,
    val baseUrl: String,
)

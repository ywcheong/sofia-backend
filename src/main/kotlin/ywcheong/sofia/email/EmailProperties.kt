package ywcheong.sofia.email

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sofia.email")
data class EmailProperties(
    val enabled: Boolean,
    val from: String,
    val fromName: String,
    val baseUrl: String,
)

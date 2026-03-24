package ywcheong.sofia.boot

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sofia.dummy")
data class SofiaDummyProperties(
    val enabled: Boolean,
)

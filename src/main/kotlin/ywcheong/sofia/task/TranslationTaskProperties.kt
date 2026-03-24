package ywcheong.sofia.task

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sofia.task")
data class TranslationTaskProperties(
    val secondsPerCharacter: Double,
    val lateThresholdHours: Long,
) {
    val lateThresholdSeconds: Long
        get() = lateThresholdHours * 60 * 60
}
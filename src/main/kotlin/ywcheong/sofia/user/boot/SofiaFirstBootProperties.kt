package ywcheong.sofia.user.boot

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sofia.first-boot")
data class SofiaFirstBootProperties(
    val createAdminIfEmpty: Boolean,
    val adminStudentNumber: String,
)

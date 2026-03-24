package ywcheong.sofia.kakao

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sofia.kakao.skill")
data class KakaoSkillSecretProperties(
    val secretToken: String,
)
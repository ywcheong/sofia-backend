package ywcheong.sofia.config.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    companion object {
        private const val SECURITY_SCHEME_NAME = "Authorization"
    }

    @Bean
    fun openAPI(): OpenAPI {
        val securityScheme = SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .`in`(SecurityScheme.In.HEADER)
            .name("Authorization")
            .description("인증 토큰을 입력하세요. 형식: `user {uuid}` 또는 `kakao {token}`")

        return OpenAPI()
            .components(Components().addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme))
            .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))
    }
}

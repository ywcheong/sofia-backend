package ywcheong.sofia.config.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority

class ApiKeyAuthenticationToken(
    private val principal: Any,
    authorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

    init { isAuthenticated = true }

    override fun getPrincipal(): Any = principal
    override fun getCredentials(): Any? = null  // 이미 검증 완료, 자격증명 불필요
}
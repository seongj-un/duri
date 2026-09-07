package com.duri.auth.oauth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

/**
 * OAuth2 로그인 성공 핸들러가 우리 쪽 userId 를 바로 알 수 있도록
 * DB 조회 결과를 principal 에 실어 나른다.
 */
class DuriOAuth2User(
    val userId: Long,
    val isNewUser: Boolean,
    private val attributes: Map<String, Any>,
) : OAuth2User {

    override fun getName(): String = userId.toString()

    override fun getAttributes(): Map<String, Any> = attributes

    override fun getAuthorities(): Collection<GrantedAuthority> = AUTHORITIES

    private companion object {
        val AUTHORITIES = listOf(SimpleGrantedAuthority("ROLE_USER"))
    }
}

package com.duri.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "duri.auth.cookie")
data class AuthCookieProperties(
    val name: String = "duri_rt",
    /**
     * 프론트엔드가 백엔드를 같은 오리진으로 프록시한다는 전제의 기본값이다.
     * 프론트를 다른 도메인에 올린다면 sameSite=None + secure=true 가 필요하다.
     */
    val sameSite: String = "Lax",
    val secure: Boolean = false,
    val path: String = "/api/v1/auth",
    val domain: String? = null,
)

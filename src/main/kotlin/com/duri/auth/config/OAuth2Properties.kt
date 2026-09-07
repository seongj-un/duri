package com.duri.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "duri.oauth2")
data class OAuth2Properties(
    /** 로그인 성공/실패 후 브라우저를 돌려보낼 프론트엔드 주소. */
    val redirectUri: String,
    /** 오픈 리다이렉트 방지를 위한 호스트 허용 목록. */
    val allowedRedirectHosts: List<String> = emptyList(),
)

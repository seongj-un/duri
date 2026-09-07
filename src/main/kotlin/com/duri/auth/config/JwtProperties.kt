package com.duri.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "duri.jwt")
data class JwtProperties(
    /** HS256 서명 키. base64 로 인코딩된 32바이트 이상. */
    val secret: String,
    val issuer: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)

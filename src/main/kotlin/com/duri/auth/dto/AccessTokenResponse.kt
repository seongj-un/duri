package com.duri.auth.dto

import com.duri.auth.application.IssuedAccessToken

data class AccessTokenResponse(
    val accessToken: String,
    val tokenType: String,
    /** 초 단위 만료까지 남은 시간. 프론트가 선제 재발급 타이밍을 잡는 데 쓴다. */
    val expiresIn: Long,
) {
    companion object {
        fun from(token: IssuedAccessToken) = AccessTokenResponse(
            accessToken = token.value,
            tokenType = "Bearer",
            expiresIn = token.expiresInSeconds,
        )
    }
}

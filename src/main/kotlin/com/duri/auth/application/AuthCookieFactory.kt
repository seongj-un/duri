package com.duri.auth.application

import com.duri.auth.config.AuthCookieProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 리프레시 토큰은 응답 본문이 아니라 HttpOnly 쿠키로만 오간다.
 * 자바스크립트가 읽을 수 없으므로 XSS 로 토큰을 통째로 훔쳐가기 어렵다.
 */
@Component
class AuthCookieFactory(
    private val properties: AuthCookieProperties,
) {

    fun create(rawToken: String, expiresAt: Instant, now: Instant): ResponseCookie =
        base(rawToken)
            .maxAge(Duration.between(now, expiresAt).coerceAtLeast(Duration.ZERO))
            .build()

    /** 로그아웃 시 즉시 만료시킨다. */
    fun expired(): ResponseCookie = base("").maxAge(Duration.ZERO).build()

    fun readFrom(request: HttpServletRequest): String? =
        request.cookies
            ?.firstOrNull { it.name == properties.name }
            ?.value
            ?.takeIf { it.isNotBlank() }

    private fun base(value: String): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie.from(properties.name, value)
            .httpOnly(true)
            .secure(properties.secure)
            .sameSite(properties.sameSite)
            .path(properties.path)
            .apply { properties.domain?.let { domain(it) } }
}

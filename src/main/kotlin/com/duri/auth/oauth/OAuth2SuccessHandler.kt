package com.duri.auth.oauth

import com.duri.auth.application.AuthCookieFactory
import com.duri.auth.application.RefreshTokenService
import com.duri.auth.config.OAuth2Properties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Clock

/**
 * 소셜 로그인이 끝나면 리프레시 토큰만 HttpOnly 쿠키로 심고 프론트로 돌려보낸다.
 *
 * 액세스 토큰을 URL 쿼리에 실어 보내지 않는 이유: 주소창·브라우저 히스토리·
 * 리퍼러·프록시 로그에 그대로 남기 때문이다. 프론트는 착지 후
 * POST /api/v1/auth/token 을 호출해 액세스 토큰을 받아간다.
 */
@Component
class OAuth2SuccessHandler(
    private val refreshTokenService: RefreshTokenService,
    private val cookieFactory: AuthCookieFactory,
    private val properties: OAuth2Properties,
    private val clock: Clock,
) : SimpleUrlAuthenticationSuccessHandler() {

    init {
        val host = runCatching { URI(properties.redirectUri).host }.getOrNull()
        require(host != null && host in properties.allowedRedirectHosts) {
            "duri.oauth2.redirect-uri 의 호스트($host)가 allowed-redirect-hosts 에 없습니다. " +
                "설정 실수로 로그인 결과가 외부로 흘러가는 것을 막기 위한 검사입니다."
        }
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as DuriOAuth2User
        val refreshToken = refreshTokenService.issueNewFamily(principal.userId)

        val cookie = cookieFactory.create(refreshToken.rawValue, refreshToken.expiresAt, clock.instant())
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())

        val target = UriComponentsBuilder.fromUriString(properties.redirectUri)
            .queryParam("status", "success")
            .queryParam("isNewUser", principal.isNewUser)
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, target)
    }
}

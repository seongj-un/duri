package com.duri.auth.api

import com.duri.auth.application.AccessTokenIssuer
import com.duri.auth.application.AuthCookieFactory
import com.duri.auth.application.RefreshTokenService
import com.duri.auth.dto.AccessTokenResponse
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val refreshTokenService: RefreshTokenService,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val cookieFactory: AuthCookieFactory,
    private val clock: Clock,
) {

    /**
     * 액세스 토큰 발급/재발급.
     *
     * 소셜 로그인 직후 프론트가 처음 호출하는 곳이자, 액세스 토큰이 만료될 때마다 다시 호출하는 곳이다.
     * 호출할 때마다 리프레시 토큰도 새 것으로 교체(회전)된다.
     */
    @PostMapping("/token")
    fun issueAccessToken(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AccessTokenResponse {
        val rawRefreshToken = cookieFactory.readFrom(request)
            ?: throw BusinessException(ErrorCode.REFRESH_TOKEN_MISSING)

        val rotated = refreshTokenService.rotate(rawRefreshToken)
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookieFactory.create(rotated.rawValue, rotated.expiresAt, clock.instant()).toString(),
        )

        return AccessTokenResponse.from(accessTokenIssuer.issue(rotated.userId))
    }

    /** 로그아웃. 쿠키가 없거나 이미 무효해도 성공으로 응답한다. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        cookieFactory.readFrom(request)?.let(refreshTokenService::revoke)
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.expired().toString())
    }
}

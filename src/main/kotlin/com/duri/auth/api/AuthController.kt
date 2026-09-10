package com.duri.auth.api

import com.duri.auth.application.AccessTokenIssuer
import com.duri.auth.application.AuthCookieFactory
import com.duri.auth.application.LocalAuthService
import com.duri.auth.application.RefreshTokenService
import com.duri.auth.dto.AccessTokenResponse
import com.duri.auth.dto.LoginRequest
import com.duri.auth.dto.SignupRequest
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val localAuthService: LocalAuthService,
    private val refreshTokenService: RefreshTokenService,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val cookieFactory: AuthCookieFactory,
    private val clock: Clock,
) {

    /** 회원가입. 곧바로 로그인 상태로 만든다. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(
        @Valid @RequestBody request: SignupRequest,
        response: HttpServletResponse,
    ): AccessTokenResponse {
        val user = localAuthService.signup(request.email, request.password, request.nickname)
        return startSession(user.requiredId, response)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): AccessTokenResponse {
        val user = localAuthService.authenticate(request.email, request.password)
        return startSession(user.requiredId, response)
    }

    /**
     * 액세스 토큰 재발급.
     *
     * 액세스 토큰이 만료될 때마다 프론트가 다시 호출하는 곳이다.
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
        writeRefreshCookie(rotated.rawValue, rotated.expiresAt, response)

        return AccessTokenResponse.from(accessTokenIssuer.issue(rotated.userId))
    }

    /** 로그아웃. 쿠키가 없거나 이미 무효해도 성공으로 응답한다. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        cookieFactory.readFrom(request)?.let(refreshTokenService::revoke)
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.expired().toString())
    }

    /** 가입과 로그인이 공유하는 마무리 절차. 리프레시 쿠키를 심고 액세스 토큰을 내려준다. */
    private fun startSession(userId: Long, response: HttpServletResponse): AccessTokenResponse {
        val issued = refreshTokenService.issueNewFamily(userId)
        writeRefreshCookie(issued.rawValue, issued.expiresAt, response)
        return AccessTokenResponse.from(accessTokenIssuer.issue(userId))
    }

    private fun writeRefreshCookie(
        rawValue: String,
        expiresAt: java.time.Instant,
        response: HttpServletResponse,
    ) = response.addHeader(
        HttpHeaders.SET_COOKIE,
        cookieFactory.create(rawValue, expiresAt, clock.instant()).toString(),
    )
}

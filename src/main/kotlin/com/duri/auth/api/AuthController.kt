package com.duri.auth.api

import com.duri.auth.application.AccessTokenIssuer
import com.duri.auth.application.AuthCookieFactory
import com.duri.auth.application.AuthRateLimiter
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
    private val rateLimiter: AuthRateLimiter,
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
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): AccessTokenResponse {
        // 가입도 로그인과 같은 카운터로 센다. 다만 로그인과 달리 성공해도 비우지 않는다.
        // 비우면 아무 이메일로나 가입해서 그 IP 의 카운터를 지우는 우회로가 생긴다.
        rateLimiter.countAttempt(clientIpOf(httpRequest), request.email)

        val user = localAuthService.signup(request.email, request.password, request.nickname)
        return startSession(user.requiredId, response)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): AccessTokenResponse {
        val clientIp = clientIpOf(httpRequest)
        rateLimiter.countAttempt(clientIp, request.email)

        val user = localAuthService.authenticate(request.email, request.password)

        // 여기까지 왔으면 정상 사용자다. 몇 번 틀렸다가 맞힌 사람이 곧바로 잠기면 안 된다.
        rateLimiter.clearAttempts(clientIp, request.email)
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

    /**
     * 헤더를 직접 파싱하지 않는다.
     *
     * 프록시 뒤에서는 FORWARD_HEADERS_STRATEGY=framework 로 띄우고(README 의 Railway 절차),
     * 그러면 스프링의 ForwardedHeaderFilter 가 X-Forwarded-For 를 읽어 remoteAddr 를
     * 실제 클라이언트 IP 로 바꿔 놓는다. 프록시가 없으면 그 설정이 none 이라
     * 위조된 X-Forwarded-For 는 무시되고 TCP 커넥션의 주소가 그대로 남는다.
     * 둘 중 어느 쪽이든 remoteAddr 가 "신뢰할 수 있는 클라이언트 IP" 다.
     */
    private fun clientIpOf(request: HttpServletRequest): String = request.remoteAddr

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

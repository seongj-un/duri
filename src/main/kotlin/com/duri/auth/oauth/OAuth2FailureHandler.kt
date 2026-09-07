package com.duri.auth.oauth

import com.duri.auth.config.OAuth2Properties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2FailureHandler(
    private val properties: OAuth2Properties,
) : SimpleUrlAuthenticationFailureHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        log.warn("social login failed: {}", exception.message)

        // 실패 원인을 그대로 노출하지 않고, 분기 가능한 코드만 프론트에 넘긴다.
        val reason = (exception as? OAuth2AuthenticationException)?.error?.errorCode ?: "LOGIN_FAILED"
        val target = UriComponentsBuilder.fromUriString(properties.redirectUri)
            .queryParam("status", "failure")
            .queryParam("reason", reason)
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, target)
    }
}

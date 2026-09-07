package com.duri.auth.config

import com.duri.auth.oauth.CustomOAuth2UserService
import com.duri.auth.oauth.OAuth2FailureHandler
import com.duri.auth.oauth.OAuth2SuccessHandler
import com.duri.common.error.ErrorCode
import com.duri.common.error.ErrorResponse
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * 필터체인을 둘로 나눈다.
 *
 * 1) 소셜 로그인 핸드셰이크 (oauth2, login/oauth2 하위 경로)
 *    - 인가 요청 state 를 리다이렉트 사이에 보관해야 하므로 세션을 허용한다.
 *    - 로그인이 끝나면 우리 토큰을 발급하고 세션은 더 쓰지 않는다.
 *
 * 2) 실제 API(그 외 전부)
 *    - 완전 무상태. Bearer 액세스 토큰만 본다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val successHandler: OAuth2SuccessHandler,
    private val failureHandler: OAuth2FailureHandler,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    @Order(1)
    fun oauth2LoginFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/oauth2/**", "/login/oauth2/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            // 브라우저 리다이렉트로만 도달하는 경로이고 상태 변경 API 가 아니다.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .oauth2Login { login ->
                login.userInfoEndpoint { it.userService(customOAuth2UserService) }
                login.successHandler(successHandler)
                login.failureHandler(failureHandler)
            }
        return http.build()
    }

    @Bean
    @Order(2)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { registry ->
                registry
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    // 아직 액세스 토큰이 없는 상태에서 호출되는 두 곳
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/token", "/api/v1/auth/logout").permitAll()
                    // 초대 미리보기는 토큰 자체가 열람 권한이라 로그인 전에도 열린다
                    .requestMatchers(HttpMethod.GET, "/api/v1/invites/*").permitAll()
                    .anyRequest().authenticated()
            }
            // 쿠키를 쓰는 엔드포인트는 리프레시 경로뿐이고, 그 쿠키가 SameSite=Lax 라
            // 크로스사이트 POST 에는 실려가지 않는다. 나머지는 Bearer 헤더 인증이라 CSRF 대상이 아니다.
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { it.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
                resourceServer.authenticationEntryPoint(::writeUnauthorized)
                resourceServer.accessDeniedHandler { request, response, _ ->
                    writeError(request, response, ErrorCode.FORBIDDEN)
                }
            }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint(::writeUnauthorized)
                handling.accessDeniedHandler { request, response, _ ->
                    writeError(request, response, ErrorCode.FORBIDDEN)
                }
            }
        return http.build()
    }

    /** scope 클레임을 쓰지 않으므로 인증된 사용자에게 일괄로 ROLE_USER 를 준다. */
    private fun jwtAuthenticationConverter() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter { listOf(SimpleGrantedAuthority("ROLE_USER")) }
    }

    private fun writeUnauthorized(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @Suppress("UNUSED_PARAMETER") exception: Exception,
    ) = writeError(request, response, ErrorCode.UNAUTHORIZED)

    /**
     * 필터 단계의 거절은 @RestControllerAdvice 까지 오지 않으므로
     * 컨트롤러 예외와 같은 형태의 JSON 을 여기서 직접 쓴다.
     */
    private fun writeError(
        request: HttpServletRequest,
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, ErrorResponse.of(errorCode, request.requestURI))
    }
}

package com.duri.auth

import com.duri.auth.application.AccessTokenIssuer
import com.duri.auth.application.RefreshTokenService
import com.duri.support.Fixtures
import com.duri.support.IntegrationTestBase
import com.duri.user.domain.UserRepository
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration

@AutoConfigureMockMvc
@DisplayName("인증 API")
class AuthApiTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val refreshTokenService: RefreshTokenService,
    private val accessTokenIssuer: AccessTokenIssuer,
) : IntegrationTestBase() {

    @Test
    fun `토큰 없이 보호된 API 를 부르면 401 과 약속된 에러 형식이 온다`() {
        mockMvc.get("/api/v1/users/me")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
                jsonPath("$.message") { exists() }
                jsonPath("$.path") { value("/api/v1/users/me") }
            }
    }

    @Test
    fun `액세스 토큰을 주면 내 정보를 돌려준다`() {
        val user = userRepository.save(Fixtures.user(nickname = "성준", email = "a@b.com"))
        val accessToken = accessTokenIssuer.issue(user.requiredId).value

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(user.requiredId) }
            jsonPath("$.nickname") { value("성준") }
            // 아직 커플이 없으므로 프론트는 온보딩으로 보내야 한다
            jsonPath("$.coupleId") { doesNotExist() }
        }
    }

    @Test
    fun `만료된 액세스 토큰은 거부된다`() {
        val user = userRepository.save(Fixtures.user())
        val accessToken = accessTokenIssuer.issue(user.requiredId).value

        // 액세스 토큰 수명은 30분. 검증기의 기본 시계 오차 허용(60초)까지 넘겨서 확인한다.
        mutableClock.advance(Duration.ofMinutes(35))

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `위조된 토큰은 거부된다`() {
        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `리프레시 쿠키가 없으면 액세스 토큰을 발급하지 않는다`() {
        mockMvc.post("/api/v1/auth/token")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("REFRESH_TOKEN_MISSING") }
            }
    }

    @Test
    fun `리프레시 쿠키를 주면 액세스 토큰을 발급하고 쿠키를 새 것으로 갈아끼운다`() {
        val user = userRepository.save(Fixtures.user())
        val issued = refreshTokenService.issueNewFamily(user.requiredId)

        val result = mockMvc.post("/api/v1/auth/token") {
            cookie(Cookie("duri_rt", issued.rawValue))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.expiresIn") { value(1800) }
        }.andReturn()

        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
        assertThat(setCookie).isNotNull()
        assertThat(setCookie).contains("duri_rt=").contains("HttpOnly").contains("SameSite=Lax")
        // 회전되었으므로 이전 토큰 값이 그대로 다시 내려오면 안 된다
        assertThat(setCookie).doesNotContain(issued.rawValue)
    }

    @Test
    fun `발급받은 액세스 토큰으로 곧바로 보호된 API 를 쓸 수 있다`() {
        val user = userRepository.save(Fixtures.user(nickname = "지현"))
        val issued = refreshTokenService.issueNewFamily(user.requiredId)

        val body = mockMvc.post("/api/v1/auth/token") {
            cookie(Cookie("duri_rt", issued.rawValue))
        }.andReturn().response.contentAsString
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.nickname") { value("지현") }
        }
    }

    @Test
    fun `로그아웃하면 쿠키가 즉시 만료되고 그 토큰은 다시 못 쓴다`() {
        val user = userRepository.save(Fixtures.user())
        val issued = refreshTokenService.issueNewFamily(user.requiredId)

        val result = mockMvc.post("/api/v1/auth/logout") {
            cookie(Cookie("duri_rt", issued.rawValue))
        }.andExpect { status { isNoContent() } }.andReturn()

        assertThat(result.response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0")

        mockMvc.post("/api/v1/auth/token") {
            cookie(Cookie("duri_rt", issued.rawValue))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("REFRESH_TOKEN_INVALID") }
        }
    }

    @Test
    fun `쿠키가 없어도 로그아웃은 성공으로 응답한다`() {
        mockMvc.post("/api/v1/auth/logout").andExpect { status { isNoContent() } }
    }
}

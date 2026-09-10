package com.duri.auth

import com.duri.support.IntegrationTestBase
import com.duri.user.domain.User
import com.duri.user.domain.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Duration

/**
 * 한도 값은 여기서 직접 정한다.
 * 운영 기본값(application.yml)을 조정해도 이 테스트가 흔들리지 않게 하려는 것이고,
 * 동시에 "한도를 프로퍼티로 조정할 수 있다" 는 요구사항 자체의 검증이기도 하다.
 *
 * 계정은 가입 엔드포인트가 아니라 리포지토리로 직접 만든다.
 * 가입도 세는 대상이라, 가입으로 준비하면 테스트가 시작부터 카운터를 하나 먹고 들어간다.
 */
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "duri.auth.rate-limit.window=PT10M",
        "duri.auth.rate-limit.max-attempts-per-email=5",
        "duri.auth.rate-limit.max-attempts-per-ip=8",
    ],
)
@DisplayName("로그인·회원가입 레이트 리밋")
class AuthRateLimitTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : IntegrationTestBase() {

    @Test
    fun `이메일당 한도까지는 통과하고 그 다음 요청은 429 다`() {
        register()

        // IP 를 매번 바꾼다. IP 축이 아니라 이메일 축에 걸렸다는 것을 분명히 하기 위해서다.
        repeat(MAX_PER_EMAIL) { attempt ->
            assertThat(login(EMAIL, WRONG_PASSWORD, ip = "10.0.0.$attempt").status)
                .describedAs("한도 안의 %d번째 시도", attempt + 1)
                .isEqualTo(401)
        }

        val blocked = login(EMAIL, WRONG_PASSWORD, ip = "10.0.0.99")

        assertThat(blocked.status).isEqualTo(429)
        assertThat(blocked.contentAsString)
            .contains("\"code\":\"TOO_MANY_AUTH_ATTEMPTS\"")
            .contains("\"path\":\"/api/v1/auth/login\"")
            .contains("\"timestamp\"")
        // 사용자에게 그대로 보여줄 수 있는 한국어 문장이어야 한다
        assertThat(messageOf(blocked.contentAsString)).matches(".*[가-힣].*")

        val retryAfter = blocked.getHeader(HttpHeaders.RETRY_AFTER)!!.toLong()
        assertThat(retryAfter).isBetween(1L, WINDOW.toSeconds())
    }

    @Test
    fun `비밀번호가 맞아도 한도를 넘긴 뒤에는 막힌다`() {
        register()

        repeat(MAX_PER_EMAIL) { login(EMAIL, WRONG_PASSWORD, ip = "10.0.1.$it") }

        assertThat(login(EMAIL, PASSWORD, ip = "10.0.1.99").status).isEqualTo(429)
    }

    @Test
    fun `IP 당 한도를 넘기면 이메일이 매번 달라도 막힌다`() {
        repeat(MAX_PER_IP) { attempt ->
            assertThat(login("nobody-$attempt@example.com", WRONG_PASSWORD, ip = SAME_IP).status)
                .describedAs("한도 안의 %d번째 시도", attempt + 1)
                .isEqualTo(401)
        }

        assertThat(login("nobody-last@example.com", WRONG_PASSWORD, ip = SAME_IP).status).isEqualTo(429)
    }

    @Test
    fun `창이 지나면 다시 시도할 수 있다`() {
        register()

        repeat(MAX_PER_EMAIL) { login(EMAIL, WRONG_PASSWORD, ip = SAME_IP) }
        assertThat(login(EMAIL, PASSWORD, ip = SAME_IP).status).isEqualTo(429)

        mutableClock.advance(WINDOW.plusSeconds(1))

        assertThat(login(EMAIL, PASSWORD, ip = SAME_IP).status).isEqualTo(200)
    }

    @Test
    fun `로그인에 성공하면 카운터가 초기화된다`() {
        register()

        // 한도 직전까지 틀린 뒤 맞힌다
        repeat(MAX_PER_EMAIL - 1) { login(EMAIL, WRONG_PASSWORD, ip = SAME_IP) }
        assertThat(login(EMAIL, PASSWORD, ip = SAME_IP).status).isEqualTo(200)

        // 비워졌으므로 처음부터 다시 한도만큼 쓸 수 있다
        repeat(MAX_PER_EMAIL) { attempt ->
            assertThat(login(EMAIL, WRONG_PASSWORD, ip = SAME_IP).status)
                .describedAs("초기화 뒤 %d번째 시도", attempt + 1)
                .isEqualTo(401)
        }
        assertThat(login(EMAIL, WRONG_PASSWORD, ip = SAME_IP).status).isEqualTo(429)
    }

    @Test
    fun `존재하지 않는 이메일도 똑같이 제한된다`() {
        repeat(MAX_PER_EMAIL) { attempt ->
            assertThat(login(UNKNOWN_EMAIL, WRONG_PASSWORD, ip = "10.0.2.$attempt").status)
                .describedAs("한도 안의 %d번째 시도", attempt + 1)
                .isEqualTo(401)
        }

        assertThat(login(UNKNOWN_EMAIL, WRONG_PASSWORD, ip = "10.0.2.99").status).isEqualTo(429)
    }

    @Test
    fun `레이트 리밋 응답으로는 가입 여부를 알 수 없다`() {
        register()

        repeat(MAX_PER_EMAIL) { login(EMAIL, WRONG_PASSWORD, ip = "10.0.3.$it") }
        repeat(MAX_PER_EMAIL) { login(UNKNOWN_EMAIL, WRONG_PASSWORD, ip = "10.0.4.$it") }

        val registered = login(EMAIL, WRONG_PASSWORD, ip = "10.0.3.99")
        val notRegistered = login(UNKNOWN_EMAIL, WRONG_PASSWORD, ip = "10.0.4.99")

        assertThat(registered.status).isEqualTo(429)
        assertThat(notRegistered.status).isEqualTo(registered.status)
        assertThat(notRegistered.getHeader(HttpHeaders.RETRY_AFTER))
            .isEqualTo(registered.getHeader(HttpHeaders.RETRY_AFTER))
        // timestamp 는 매번 달라지므로 분간에 쓸 수 있는 값만 비교한다
        assertThat(notRegistered.contentAsString.replace(TIMESTAMP, ""))
            .isEqualTo(registered.contentAsString.replace(TIMESTAMP, ""))
    }

    @Test
    fun `대소문자나 공백을 섞어도 같은 이메일로 센다`() {
        register()

        val disguises = listOf(
            EMAIL,
            EMAIL.uppercase(),
            " $EMAIL ",
            "SeongJun@Example.COM",
            "  seongjun@EXAMPLE.com",
        )
        check(disguises.size == MAX_PER_EMAIL) { "위장 목록은 이메일 한도만큼이어야 한다" }

        disguises.forEachIndexed { index, disguise ->
            assertThat(login(disguise, WRONG_PASSWORD, ip = "10.0.5.$index").status)
                .describedAs("한도 안의 %d번째 시도", index + 1)
                .isEqualTo(401)
        }

        assertThat(login(EMAIL, WRONG_PASSWORD, ip = "10.0.5.99").status).isEqualTo(429)
    }

    @Test
    fun `회원가입도 제한된다`() {
        // 첫 요청은 성공하고 나머지는 이메일 중복으로 막히지만, 세는 것은 시도 횟수다
        repeat(MAX_PER_EMAIL) { attempt ->
            assertThat(signup(ip = "10.0.6.$attempt").status)
                .describedAs("한도 안의 %d번째 시도", attempt + 1)
                .isIn(201, 409)
        }

        val blocked = signup(ip = "10.0.6.99")

        assertThat(blocked.status).isEqualTo(429)
        assertThat(blocked.contentAsString).contains("\"code\":\"TOO_MANY_AUTH_ATTEMPTS\"")
        assertThat(blocked.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull()
    }

    /** 가입 엔드포인트를 거치지 않고 로그인 가능한 계정을 만든다. */
    private fun register() = userRepository.save(
        User.register(
            email = EMAIL,
            passwordHash = requireNotNull(passwordEncoder.encode(PASSWORD)),
            nickname = "성준",
        ),
    )

    private fun signup(ip: String) = mockMvc.post("/api/v1/auth/signup") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$EMAIL","password":"$PASSWORD","nickname":"성준"}"""
        with { request -> request.also { it.remoteAddr = ip } }
    }.andReturn().response

    private fun login(email: String, password: String, ip: String) = mockMvc.post("/api/v1/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$email","password":"$password"}"""
        with { request -> request.also { it.remoteAddr = ip } }
    }.andReturn().response

    private fun messageOf(body: String) = Regex(""""message":"([^"]+)"""").find(body)!!.groupValues[1]

    private companion object {
        const val MAX_PER_EMAIL = 5
        const val MAX_PER_IP = 8
        val WINDOW: Duration = Duration.ofMinutes(10)

        const val EMAIL = "seongjun@example.com"
        const val UNKNOWN_EMAIL = "nobody@example.com"
        const val PASSWORD = "password123"
        const val WRONG_PASSWORD = "wrong-password"

        const val SAME_IP = "10.1.1.1"

        val TIMESTAMP = Regex(""""timestamp":"[^"]+"""")
    }
}

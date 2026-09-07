package com.duri.couple

import com.duri.auth.application.AccessTokenIssuer
import com.duri.support.Fixtures
import com.duri.support.IntegrationTestBase
import com.duri.user.domain.UserRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
@DisplayName("커플 space API")
class CoupleApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val accessTokenIssuer: AccessTokenIssuer,
) : IntegrationTestBase() {

    private fun tokenFor(nickname: String = "성준"): Pair<Long, String> {
        val user = userRepository.save(Fixtures.user(nickname = nickname))
        return user.requiredId to accessTokenIssuer.issue(user.requiredId).value
    }

    @Test
    fun `space 를 만들면 201 과 함께 PENDING 상태로 돌아온다`() {
        val (_, token) = tokenFor()

        mockMvc.post("/api/v1/couples") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"우리집 가계부"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("우리집 가계부") }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.members.length()") { value(1) }
        }
    }

    @Test
    fun `이름이 비어 있으면 400 과 필드 오류를 돌려준다`() {
        val (_, token) = tokenFor()

        mockMvc.post("/api/v1/couples") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"  "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.fieldErrors[0].field") { value("name") }
        }
    }

    @Test
    fun `커플이 없는 사용자가 내 space 를 조회하면 404 다`() {
        val (_, token) = tokenFor()

        mockMvc.get("/api/v1/couples/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COUPLE_NOT_FOUND") }
        }
    }

    @Test
    fun `초대 링크 발급부터 수락까지 HTTP 로 이어진다`() {
        val (_, ownerToken) = tokenFor("성준")
        val (_, partnerToken) = tokenFor("지현")

        mockMvc.post("/api/v1/couples") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"우리집"}"""
        }.andExpect { status { isCreated() } }

        val inviteBody = mockMvc.post("/api/v1/couples/me/invites") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.inviteUrl") { exists() }
        }.andReturn().response.contentAsString
        val inviteToken = Regex("\"token\":\"([^\"]+)\"").find(inviteBody)!!.groupValues[1]

        // 미리보기는 로그인 없이도 열린다
        mockMvc.get("/api/v1/invites/$inviteToken").andExpect {
            status { isOk() }
            jsonPath("$.inviterNickname") { value("성준") }
            jsonPath("$.status") { value("PENDING") }
        }

        mockMvc.post("/api/v1/invites/$inviteToken/accept") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/couples/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
            jsonPath("$.members.length()") { value(2) }
        }
    }

    @Test
    fun `정산 기준일은 28일을 넘길 수 없다`() {
        val (_, token) = tokenFor()
        mockMvc.post("/api/v1/couples") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"우리집"}"""
        }

        mockMvc.patch("/api/v1/couples/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"settlementDay":31}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].field") { value("settlementDay") }
        }
    }
}

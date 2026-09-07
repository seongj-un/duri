package com.duri.realtime

import com.duri.auth.application.AccessTokenIssuer
import com.duri.realtime.application.CoupleEventBroker
import com.duri.realtime.domain.CoupleEvent
import com.duri.realtime.domain.CoupleEventType
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@AutoConfigureMockMvc
@DisplayName("실시간 이벤트 스트림")
class EventStreamApiTest(
    private val mockMvc: MockMvc,
    private val broker: CoupleEventBroker,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
        token = accessTokenIssuer.issue(couple.ownerId).value
    }

    /**
     * MockMvc 는 비동기 요청을 끝내 주지 않아 SSE 연결이 그대로 남는다.
     * 브로커는 싱글턴이고 TRUNCATE RESTART IDENTITY 로 커플 id 까지 되살아나므로
     * 닫아 주지 않으면 다음 테스트에 연결이 새어 들어간다.
     */
    @AfterEach
    fun closeStreams() {
        broker.closeAll(couple.coupleId)
    }

    private fun subscribe() = mockMvc.get("/api/v1/events") {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }.andExpect {
        request { asyncStarted() }
    }.andReturn()

    @Test
    fun `구독하면 연결이 등록되고 첫 신호를 받는다`() {
        val result = subscribe()

        assertThat(broker.connectionCount(couple.coupleId)).isEqualTo(1)
        assertThat(result.response.contentAsString).contains("connected")
    }

    @Test
    fun `커플에서 일어난 변화가 스트림으로 전달된다`() {
        val result = subscribe()

        broker.publish(
            CoupleEvent(
                type = CoupleEventType.EXPENSE_CREATED,
                coupleId = couple.coupleId,
                actorId = couple.partnerId,
                resourceId = 42L,
                period = "2026-01",
                occurredAt = Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )

        val body = result.response.contentAsString
        assertThat(body).contains("event:EXPENSE_CREATED")
        assertThat(body).contains("\"resourceId\":42")
        assertThat(body).contains("\"period\":\"2026-01\"")
    }

    @Test
    fun `다른 커플의 이벤트는 흘러들지 않는다`() {
        val result = subscribe()
        val other = coupleFixture.active("A", "B")

        broker.publish(
            CoupleEvent(
                type = CoupleEventType.SETTLEMENT_CONFIRMED,
                coupleId = other.coupleId,
                actorId = other.ownerId,
                resourceId = 1L,
                period = "2026-01",
                occurredAt = Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )

        assertThat(result.response.contentAsString).doesNotContain("SETTLEMENT_CONFIRMED")
    }

    @Test
    fun `토큰 없이는 구독할 수 없다`() {
        mockMvc.get("/api/v1/events").andExpect { status { isUnauthorized() } }
    }
}

package com.duri.settlement

import com.duri.auth.application.AccessTokenIssuer
import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDate

@AutoConfigureMockMvc
@DisplayName("정산 · 계좌 API")
class SettlementApiTest(
    private val mockMvc: MockMvc,
    private val expenseService: ExpenseService,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple
    private lateinit var ownerToken: String
    private lateinit var partnerToken: String

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
        ownerToken = accessTokenIssuer.issue(couple.ownerId).value
        partnerToken = accessTokenIssuer.issue(couple.partnerId).value
    }

    private fun bearer(token: String) = HttpHeaders.AUTHORIZATION to "Bearer $token"

    private fun spend(payerId: Long, amount: Long, day: Int = 15, month: Int = 1) =
        expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(
                payerId, amount, ExpenseCategory.GROCERY, 50, LocalDate.of(2026, month, day), null,
            ),
        )

    @Test
    fun `계좌를 등록하고 다시 조회할 수 있다`() {
        mockMvc.put("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"bank":"KAKAOBANK","accountNo":"3333-01-1234567","holderName":"박성준"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.bank") { value("KAKAOBANK") }
            jsonPath("$.bankName") { value("카카오뱅크") }
            jsonPath("$.accountNo") { value("3333011234567") }
        }

        mockMvc.get("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.holderName") { value("박성준") }
        }
    }

    @Test
    fun `계좌가 없으면 404 로 알려준다`() {
        mockMvc.get("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ACCOUNT_NOT_REGISTERED") }
        }
    }

    @Test
    fun `계좌번호 형식이 맞지 않으면 400 이다`() {
        mockMvc.put("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"bank":"KB","accountNo":"계좌번호아님","holderName":"박성준"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].field") { value("accountNo") }
        }
    }

    @Test
    fun `계좌를 삭제할 수 있다`() {
        mockMvc.put("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"bank":"KB","accountNo":"12345678901234","holderName":"박성준"}"""
        }

        mockMvc.delete("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `확정 전 정산 현황은 OPEN 으로 내려온다`() {
        spend(couple.ownerId, 30_000)

        mockMvc.get("/api/v1/settlements/2026-01") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.period") { value("2026-01") }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.settlementId") { doesNotExist() }
            jsonPath("$.netAmount") { value(15000) }
            jsonPath("$.expenseCount") { value(1) }
            jsonPath("$.totalAmount") { value(30000) }
        }
    }

    @Test
    fun `확정하면 계좌와 복사 문구까지 한 번에 내려온다`() {
        mockMvc.put("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"bank":"KAKAOBANK","accountNo":"3333011234567","holderName":"박성준"}"""
        }
        spend(couple.ownerId, 1_420_000)

        mockMvc.post("/api/v1/settlements/2026-01/confirm") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
            jsonPath("$.netAmount") { value(710000) }
            jsonPath("$.creditor.nickname") { value("성준") }
            jsonPath("$.debtor.nickname") { value("지현") }
            jsonPath("$.transfer.bankName") { value("카카오뱅크") }
            jsonPath("$.transfer.accountNo") { value("3333011234567") }
            jsonPath("$.transfer.copyText") { value("카카오뱅크 3333011234567 박성준 710,000원") }
            jsonPath("$.confirmedBy.nickname") { value("성준") }
        }
    }

    @Test
    fun `채무자도 상대 계좌를 볼 수 있어야 송금할 수 있다`() {
        mockMvc.put("/api/v1/users/me/account") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"bank":"KB","accountNo":"12345678901234","holderName":"박성준"}"""
        }
        spend(couple.ownerId, 30_000)

        mockMvc.get("/api/v1/settlements/2026-01") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.debtor.nickname") { value("지현") }
            jsonPath("$.transfer.accountNo") { value("12345678901234") }
            jsonPath("$.transfer.amount") { value(15000) }
        }
    }

    @Test
    fun `확정을 두 번 호출해도 같은 결과가 온다`() {
        spend(couple.ownerId, 30_000)

        val first = mockMvc.post("/api/v1/settlements/2026-01/confirm") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val second = mockMvc.post("/api/v1/settlements/2026-01/confirm") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first)
    }

    @Test
    fun `period 를 생략한 확정은 지난달을 마감한다`() {
        // 테스트 시계는 2026-01-01 이므로 지난달은 2025-12
        mockMvc.post("/api/v1/settlements/confirm") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.period") { value("2025-12") }
            jsonPath("$.status") { value("CONFIRMED") }
        }
    }

    @Test
    fun `아직 오지 않은 달을 확정하면 400 이다`() {
        mockMvc.post("/api/v1/settlements/2026-02/confirm") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("FUTURE_PERIOD_NOT_SETTLEABLE") }
        }
    }

    @Test
    fun `정산 이력을 목록으로 볼 수 있다`() {
        spend(couple.ownerId, 30_000)
        mockMvc.post("/api/v1/settlements/2026-01/confirm") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }

        mockMvc.get("/api/v1/settlements") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].period") { value("2026-01") }
            jsonPath("$[0].netAmount") { value(15000) }
            jsonPath("$[0].status") { value("CONFIRMED") }
        }
    }

    @Test
    fun `토큰 없이는 정산과 계좌에 접근할 수 없다`() {
        mockMvc.get("/api/v1/settlements").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/settlements/2026-01").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/users/me/account").andExpect { status { isUnauthorized() } }
    }
}

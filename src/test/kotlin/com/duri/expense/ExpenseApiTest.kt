package com.duri.expense

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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDate

@AutoConfigureMockMvc
@DisplayName("지출 API")
class ExpenseApiTest(
    private val mockMvc: MockMvc,
    private val expenseService: ExpenseService,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple
    private lateinit var ownerToken: String

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
        ownerToken = accessTokenIssuer.issue(couple.ownerId).value
    }

    private fun seed(
        amount: Long,
        payerId: Long = couple.ownerId,
        category: ExpenseCategory = ExpenseCategory.GROCERY,
        day: Int = 15,
        month: Int = 1,
    ) = expenseService.create(
        couple.ownerId,
        ExpenseCreateRequest(payerId, amount, category, 50, LocalDate.of(2026, month, day), null),
    )

    @Test
    fun `지출을 등록하면 201 과 계산된 몫이 돌아온다`() {
        mockMvc.post("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"payerId":${couple.ownerId},"amount":30000,"category":"GROCERY",
                 "payerBurdenRate":50,"spentAt":"2026-01-15","memo":"이마트"}
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.amount") { value(30000) }
            jsonPath("$.payerShare") { value(15000) }
            jsonPath("$.partnerShare") { value(15000) }
            jsonPath("$.categoryName") { value("장보기") }
            jsonPath("$.locked") { value(false) }
        }
    }

    @Test
    fun `잘못된 값은 400 과 필드 오류로 돌려준다`() {
        mockMvc.post("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"payerId":${couple.ownerId},"amount":-100,"category":"GROCERY","spentAt":"2026-01-15"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.fieldErrors[0].field") { value("amount") }
        }
    }

    @Test
    fun `부담 비율이 100 을 넘으면 거부된다`() {
        mockMvc.post("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"payerId":${couple.ownerId},"amount":1000,"category":"ETC",
                 "payerBurdenRate":120,"spentAt":"2026-01-15"}
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].field") { value("payerBurdenRate") }
        }
    }

    @Test
    fun `월별 목록은 그 달 것만 최신순으로 주고 필터 전체 합계를 함께 준다`() {
        seed(amount = 10_000, day = 3)
        seed(amount = 20_000, day = 20)
        seed(amount = 99_000, month = 2)

        mockMvc.get("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            param("period", "2026-01")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(2) }
            jsonPath("$.totalAmount") { value(30000) }
            jsonPath("$.content[0].amount") { value(20000) }   // 최신순
            jsonPath("$.content[1].amount") { value(10000) }
            jsonPath("$.hasNext") { value(false) }
        }
    }

    @Test
    fun `카테고리와 결제자로 걸러낼 수 있다`() {
        seed(amount = 10_000, category = ExpenseCategory.GROCERY, payerId = couple.ownerId)
        seed(amount = 20_000, category = ExpenseCategory.DATE, payerId = couple.ownerId)
        seed(amount = 30_000, category = ExpenseCategory.DATE, payerId = couple.partnerId)

        mockMvc.get("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            param("period", "2026-01")
            param("category", "DATE")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(2) }
            jsonPath("$.totalAmount") { value(50000) }
        }

        mockMvc.get("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            param("period", "2026-01")
            param("payerId", couple.partnerId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.totalAmount") { value(30000) }
        }
    }

    @Test
    fun `페이지 크기를 넘기면 다음 페이지가 있다고 알려준다`() {
        repeat(3) { seed(amount = 1_000L * (it + 1), day = it + 1) }

        mockMvc.get("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            param("period", "2026-01")
            param("size", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.totalElements") { value(3) }
            jsonPath("$.totalPages") { value(2) }
            jsonPath("$.hasNext") { value(true) }
            // 합계는 페이지가 아니라 필터 전체 기준
            jsonPath("$.totalAmount") { value(6000) }
        }
    }

    @Test
    fun `수정과 삭제가 HTTP 로 이어진다`() {
        val created = seed(amount = 10_000)

        mockMvc.patch("/api/v1/expenses/${created.expenseId}") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":25000,"category":"DINING"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.amount") { value(25000) }
            jsonPath("$.categoryName") { value("외식") }
        }

        mockMvc.delete("/api/v1/expenses/${created.expenseId}") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/expenses/${created.expenseId}") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("EXPENSE_NOT_FOUND") }
        }
    }

    @Test
    fun `월별 요약이 한 번에 내려온다`() {
        seed(amount = 600_000, category = ExpenseCategory.RENT, payerId = couple.ownerId)
        seed(amount = 200_000, category = ExpenseCategory.DATE, payerId = couple.partnerId)

        mockMvc.get("/api/v1/summaries/monthly") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
            param("period", "2026-01")
        }.andExpect {
            status { isOk() }
            jsonPath("$.period") { value("2026-01") }
            jsonPath("$.from") { value("2026-01-01") }
            jsonPath("$.to") { value("2026-01-31") }
            jsonPath("$.totalAmount") { value(800000) }
            jsonPath("$.expenseCount") { value(2) }
            jsonPath("$.members.length()") { value(2) }
            jsonPath("$.categories.length()") { value(2) }
            jsonPath("$.balance.netAmount") { value(200000) }
            jsonPath("$.balance.creditor.nickname") { value("성준") }
            jsonPath("$.balance.debtor.nickname") { value("지현") }
            jsonPath("$.settlementStatus") { doesNotExist() }
        }
    }

    @Test
    fun `period 를 생략하면 이번 달을 본다`() {
        // 테스트 시계는 2026-01-01 에 고정되어 있다
        seed(amount = 5_000, month = 1, day = 20)
        seed(amount = 7_000, month = 2, day = 20)

        mockMvc.get("/api/v1/expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalAmount") { value(5000) }
        }
    }

    @Test
    fun `토큰 없이는 지출에 접근할 수 없다`() {
        mockMvc.get("/api/v1/expenses").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/summaries/monthly").andExpect { status { isUnauthorized() } }
    }
}

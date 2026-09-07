package com.duri.expense

import com.duri.auth.application.AccessTokenIssuer
import com.duri.expense.application.RecurringExpenseScheduler
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
import org.springframework.test.web.servlet.put
import java.time.LocalDate

@AutoConfigureMockMvc
@DisplayName("반복지출 · 부담비율 프리셋 API")
class RecurringExpenseApiTest(
    private val mockMvc: MockMvc,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val scheduler: RecurringExpenseScheduler,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
        token = accessTokenIssuer.issue(couple.ownerId).value
    }

    private fun createRent(day: Int = 25, startsOn: String = "2026-01-01") =
        mockMvc.post("/api/v1/recurring-expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"payerId":${couple.ownerId},"title":"월세","amount":1000000,"category":"RENT",
                 "payerBurdenRate":50,"dayOfMonth":$day,"startsOn":"$startsOn"}
            """.trimIndent()
        }

    @Test
    fun `반복지출을 등록하면 다음 발생일까지 알려준다`() {
        createRent(day = 25).andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("월세") }
            jsonPath("$.categoryName") { value("월세") }
            jsonPath("$.dayOfMonth") { value(25) }
            jsonPath("$.active") { value(true) }
            // 테스트 시계는 2026-01-01
            jsonPath("$.nextDueDate") { value("2026-01-25") }
        }
    }

    @Test
    fun `발생일이 범위를 벗어나면 400 이다`() {
        mockMvc.post("/api/v1/recurring-expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"payerId":${couple.ownerId},"title":"월세","amount":1000000,
                 "category":"RENT","dayOfMonth":31}
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors[0].field") { value("dayOfMonth") }
        }
    }

    @Test
    fun `목록과 수정, 중지가 이어진다`() {
        val body = createRent().andReturn().response.contentAsString
        val id = Regex("\"recurringExpenseId\":(\\d+)").find(body)!!.groupValues[1]

        mockMvc.patch("/api/v1/recurring-expenses/$id") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"amount":1100000,"memo":"관리비 포함"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.amount") { value(1100000) }
            jsonPath("$.memo") { value("관리비 포함") }
        }

        mockMvc.post("/api/v1/recurring-expenses/$id/deactivate") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(false) }
            jsonPath("$.nextDueDate") { doesNotExist() }
        }

        mockMvc.get("/api/v1/recurring-expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].active") { value(false) }
        }
    }

    @Test
    fun `아직 아무것도 만들지 않았으면 삭제할 수 있다`() {
        val body = createRent().andReturn().response.contentAsString
        val id = Regex("\"recurringExpenseId\":(\\d+)").find(body)!!.groupValues[1]

        mockMvc.delete("/api/v1/recurring-expenses/$id") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/recurring-expenses") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `이미 지출을 만든 반복지출은 삭제 대신 중지를 안내한다`() {
        val body = createRent(day = 5).andReturn().response.contentAsString
        val id = Regex("\"recurringExpenseId\":(\\d+)").find(body)!!.groupValues[1]
        scheduler.generateFor(LocalDate.of(2026, 1, 5))

        mockMvc.delete("/api/v1/recurring-expenses/$id") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("RECURRING_EXPENSE_IN_USE") }
        }
    }

    @Test
    fun `프리셋은 전체 카테고리가 내려오고 저장하면 두 사람 몫이 채워진다`() {
        mockMvc.get("/api/v1/couples/me/burden-presets") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(10) }
            jsonPath("$[0].customized") { value(false) }
        }

        mockMvc.put("/api/v1/couples/me/burden-presets") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"presets":[{"category":"RENT","userId":${couple.ownerId},"burdenRate":30}]}"""
        }.andExpect {
            status { isOk() }
            // RENT 는 ExpenseCategory 의 첫 상수라 응답에서도 첫 항목이다
            jsonPath("$[0].category") { value("RENT") }
            jsonPath("$[0].customized") { value(true) }
            jsonPath("$[0].rates[0].burdenRate") { value(30) }
            jsonPath("$[0].rates[1].burdenRate") { value(70) }
            jsonPath("$[1].customized") { value(false) }
        }
    }

    @Test
    fun `프리셋 비율이 100 을 넘으면 400 이다`() {
        mockMvc.put("/api/v1/couples/me/burden-presets") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"presets":[{"category":"RENT","userId":${couple.ownerId},"burdenRate":150}]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `토큰 없이는 접근할 수 없다`() {
        mockMvc.get("/api/v1/recurring-expenses").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/couples/me/burden-presets").andExpect { status { isUnauthorized() } }
    }
}

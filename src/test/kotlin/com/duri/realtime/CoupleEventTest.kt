package com.duri.realtime

import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.expense.dto.ExpenseUpdateRequest
import com.duri.realtime.domain.CoupleEvent
import com.duri.realtime.domain.CoupleEventType
import com.duri.settlement.application.SettlementService
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.LocalDate
import java.time.YearMonth

@RecordApplicationEvents
@DisplayName("커플 변경 이벤트 발행")
class CoupleEventTest(
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    /** ApplicationEvents 는 빈이 아니라 테스트 확장이 채워 주므로 필드로 받는다. */
    @Autowired
    private lateinit var events: ApplicationEvents

    private lateinit var couple: ActiveCouple

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
    }

    private fun published() = events.stream(CoupleEvent::class.java).toList()

    private fun spend(amount: Long = 30_000) = expenseService.create(
        couple.ownerId,
        ExpenseCreateRequest(
            couple.ownerId, amount, ExpenseCategory.GROCERY, 50, LocalDate.of(2026, 1, 15), null,
        ),
    )

    @Test
    fun `지출을 만들면 어느 달이 바뀌었는지까지 알린다`() {
        val expense = spend()

        val event = published().single()
        assertThat(event.type).isEqualTo(CoupleEventType.EXPENSE_CREATED)
        assertThat(event.coupleId).isEqualTo(couple.coupleId)
        assertThat(event.actorId).isEqualTo(couple.ownerId)
        assertThat(event.resourceId).isEqualTo(expense.expenseId)
        assertThat(event.period).isEqualTo("2026-01")
    }

    @Test
    fun `수정과 삭제도 각각 알린다`() {
        val expense = spend()
        expenseService.update(couple.partnerId, expense.expenseId, ExpenseUpdateRequest(amount = 50_000))
        expenseService.delete(couple.partnerId, expense.expenseId)

        assertThat(published().map { it.type }).containsExactly(
            CoupleEventType.EXPENSE_CREATED,
            CoupleEventType.EXPENSE_UPDATED,
            CoupleEventType.EXPENSE_DELETED,
        )
        // 상대가 한 변경이므로 actor 는 파트너다
        assertThat(published().last().actorId).isEqualTo(couple.partnerId)
    }

    @Test
    fun `정산을 확정하면 알린다`() {
        spend()

        settlementService.confirm(couple.ownerId, YearMonth.of(2026, 1))

        assertThat(published().map { it.type })
            .containsExactly(CoupleEventType.EXPENSE_CREATED, CoupleEventType.SETTLEMENT_CONFIRMED)
    }

    @Test
    fun `실패한 요청은 아무것도 알리지 않는다`() {
        runCatching {
            expenseService.create(
                couple.ownerId,
                ExpenseCreateRequest(
                    // 커플 밖의 사람을 결제자로 지정해 실패시킨다
                    payerId = -1L,
                    amount = 10_000,
                    category = ExpenseCategory.ETC,
                    payerBurdenRate = 50,
                    spentAt = LocalDate.of(2026, 1, 1),
                    memo = null,
                ),
            )
        }

        assertThat(published()).isEmpty()
    }
}

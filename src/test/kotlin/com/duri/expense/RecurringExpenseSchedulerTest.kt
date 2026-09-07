package com.duri.expense

import com.duri.expense.application.ExpenseService
import com.duri.expense.application.RecurringExpenseScheduler
import com.duri.expense.application.RecurringExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.dto.RecurringExpenseCreateRequest
import com.duri.expense.dto.RecurringExpenseResponse
import com.duri.settlement.application.SettlementService
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("반복지출 자동 생성")
class RecurringExpenseSchedulerTest(
    private val recurringExpenseService: RecurringExpenseService,
    private val scheduler: RecurringExpenseScheduler,
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val expenseRepository: ExpenseRepository,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
    }

    private fun rent(
        day: Int = 25,
        amount: Long = 1_000_000,
        startsOn: LocalDate = LocalDate.of(2026, 1, 1),
        endsOn: LocalDate? = null,
    ): RecurringExpenseResponse = recurringExpenseService.create(
        couple.ownerId,
        RecurringExpenseCreateRequest(
            payerId = couple.ownerId,
            title = "월세",
            amount = amount,
            category = ExpenseCategory.RENT,
            payerBurdenRate = 50,
            dayOfMonth = day,
            startsOn = startsOn,
            endsOn = endsOn,
            memo = null,
        ),
    )

    @Test
    fun `발생일이 되면 지출이 만들어진다`() {
        val recurring = rent(day = 25)

        val report = scheduler.generateFor(LocalDate.of(2026, 1, 25))

        assertThat(report.created).isEqualTo(1)
        val created = expenseRepository.findAll().single()
        assertThat(created.amount).isEqualTo(1_000_000)
        assertThat(created.spentAt).isEqualTo(LocalDate.of(2026, 1, 25))
        assertThat(created.category).isEqualTo(ExpenseCategory.RENT)
        assertThat(created.memo).isEqualTo("월세")
        assertThat(created.recurringExpenseId).isEqualTo(recurring.recurringExpenseId)
        assertThat(created.isGenerated).isTrue()
    }

    @Test
    fun `여러 번 돌려도 한 달에 한 건만 만든다`() {
        rent(day = 25)

        scheduler.generateFor(LocalDate.of(2026, 1, 25))
        val second = scheduler.generateFor(LocalDate.of(2026, 1, 25))
        val third = scheduler.generateFor(LocalDate.of(2026, 1, 28))

        assertThat(second.created).isZero()
        assertThat(third.created).isZero()
        assertThat(expenseRepository.findAll()).hasSize(1)
    }

    @Test
    fun `발생일 전에는 만들지 않는다`() {
        rent(day = 25)

        val report = scheduler.generateFor(LocalDate.of(2026, 1, 24))

        assertThat(report.scanned).isZero()
        assertThat(expenseRepository.findAll()).isEmpty()
    }

    @Test
    fun `서버가 발생일에 꺼져 있었어도 다음 실행에서 따라잡는다`() {
        rent(day = 25)

        // 25일에 못 돌고 28일에야 돌았다
        val report = scheduler.generateFor(LocalDate.of(2026, 1, 28))

        assertThat(report.created).isEqualTo(1)
        // 지출 날짜는 실행일이 아니라 발생일로 남는다
        assertThat(expenseRepository.findAll().single().spentAt).isEqualTo(LocalDate.of(2026, 1, 25))
    }

    @Test
    fun `다음 달이 되면 다시 만든다`() {
        rent(day = 25)

        scheduler.generateFor(LocalDate.of(2026, 1, 25))
        scheduler.generateFor(LocalDate.of(2026, 2, 25))

        val spentDates = expenseRepository.findAll().map { it.spentAt }.sorted()
        assertThat(spentDates).containsExactly(LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25))
    }

    @Test
    fun `시작일 전의 발생일은 건너뛴다`() {
        rent(day = 5, startsOn = LocalDate.of(2026, 1, 10))

        val january = scheduler.generateFor(LocalDate.of(2026, 1, 20))
        val february = scheduler.generateFor(LocalDate.of(2026, 2, 20))

        assertThat(january.created).isZero()
        assertThat(february.created).isEqualTo(1)
    }

    @Test
    fun `종료일이 지나면 더 만들지 않는다`() {
        rent(day = 5, endsOn = LocalDate.of(2026, 1, 31))

        scheduler.generateFor(LocalDate.of(2026, 1, 5))
        val february = scheduler.generateFor(LocalDate.of(2026, 2, 5))

        assertThat(february.created).isZero()
        assertThat(expenseRepository.findAll()).hasSize(1)
    }

    @Test
    fun `중지하면 더 만들지 않는다`() {
        val recurring = rent(day = 5)
        recurringExpenseService.setActive(couple.ownerId, recurring.recurringExpenseId, false)

        val report = scheduler.generateFor(LocalDate.of(2026, 1, 5))

        assertThat(report.scanned).isZero()
        assertThat(expenseRepository.findAll()).isEmpty()
    }

    @Test
    fun `사용자가 지운 자동 생성 건은 되살아나지 않는다`() {
        rent(day = 5)
        scheduler.generateFor(LocalDate.of(2026, 1, 5))
        val generated = expenseRepository.findAll().single()
        expenseService.delete(couple.ownerId, generated.requiredId)

        val report = scheduler.generateFor(LocalDate.of(2026, 1, 10))

        assertThat(report.created).isZero()
        assertThat(expenseRepository.findAll()).hasSize(1)
        assertThat(expenseRepository.findAll().single().isDeleted).isTrue()
    }

    @Test
    fun `이미 확정된 달에는 끼워 넣지 않는다`() {
        rent(day = 25)
        settlementService.confirm(couple.ownerId, YearMonth.of(2026, 1))

        val report = scheduler.generateFor(LocalDate.of(2026, 1, 25))

        assertThat(report.created).isZero()
        assertThat(report.skipped).isEqualTo(1)
        assertThat(expenseRepository.findAll()).isEmpty()
    }

    @Test
    fun `파트너가 없는 space 에는 만들지 않는다`() {
        // 활성 커플에 반복지출을 만들어 두고, 다른 커플은 PENDING 상태로 둔다
        rent(day = 5)
        coupleFixture.pending("혼자")

        val report = scheduler.generateFor(LocalDate.of(2026, 1, 5))

        assertThat(report.created).isEqualTo(1)
        assertThat(expenseRepository.findAll()).hasSize(1)
    }

    @Test
    fun `자동 생성된 지출도 월별 집계에 그대로 들어간다`() {
        rent(day = 5, amount = 1_000_000)
        scheduler.generateFor(LocalDate.of(2026, 1, 5))

        val settlement = settlementService.get(couple.ownerId, YearMonth.of(2026, 1))

        assertThat(settlement.totalAmount).isEqualTo(1_000_000)
        assertThat(settlement.netAmount).isEqualTo(500_000)
        assertThat(settlement.creditor?.userId).isEqualTo(couple.ownerId)
    }

    @Test
    fun `자동 생성된 지출도 사람이 넣은 것처럼 수정할 수 있다`() {
        rent(day = 5, amount = 1_000_000)
        scheduler.generateFor(LocalDate.of(2026, 1, 5))
        val generated = expenseRepository.findAll().single()

        val updated = expenseService.update(
            couple.partnerId,
            generated.requiredId,
            com.duri.expense.dto.ExpenseUpdateRequest(amount = 1_050_000, memo = "관리비 인상"),
        )

        assertThat(updated.amount).isEqualTo(1_050_000)
        assertThat(updated.memo).isEqualTo("관리비 인상")
    }
}

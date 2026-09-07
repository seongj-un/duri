package com.duri.expense

import com.duri.expense.application.ExpenseService
import com.duri.expense.application.MonthlySummaryService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("월별 요약과 순잔액")
class MonthlySummaryServiceTest(
    private val expenseService: ExpenseService,
    private val summaryService: MonthlySummaryService,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple
    private val january = YearMonth.of(2026, 1)

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active(ownerNickname = "성준", partnerNickname = "지현")
    }

    private fun spend(
        payerId: Long,
        amount: Long,
        rate: Int = 50,
        category: ExpenseCategory = ExpenseCategory.GROCERY,
        day: Int = 15,
        month: Int = 1,
    ) = expenseService.create(
        couple.ownerId,
        ExpenseCreateRequest(payerId, amount, category, rate, LocalDate.of(2026, month, day), null),
    )

    @Test
    fun `지출이 없으면 순잔액은 0 이고 채권자도 채무자도 없다`() {
        val summary = summaryService.monthly(couple.ownerId, january)

        assertThat(summary.totalAmount).isZero()
        assertThat(summary.expenseCount).isZero()
        assertThat(summary.balance.netAmount).isZero()
        assertThat(summary.balance.creditor).isNull()
        assertThat(summary.balance.debtor).isNull()
    }

    @Test
    fun `한 사람만 결제했으면 상대가 절반을 갚아야 한다`() {
        spend(payerId = couple.ownerId, amount = 30_000)

        val balance = summaryService.monthly(couple.ownerId, january).balance

        assertThat(balance.netAmount).isEqualTo(15_000)
        assertThat(balance.creditor?.nickname).isEqualTo("성준")
        assertThat(balance.debtor?.nickname).isEqualTo("지현")
    }

    @Test
    fun `서로 결제한 건들이 상계되어 차액만 남는다`() {
        spend(payerId = couple.ownerId, amount = 30_000)    // 지현이 성준에게 15,000
        spend(payerId = couple.partnerId, amount = 10_000)  // 성준이 지현에게 5,000

        val summary = summaryService.monthly(couple.ownerId, january)

        assertThat(summary.totalAmount).isEqualTo(40_000)
        assertThat(summary.expenseCount).isEqualTo(2)
        assertThat(summary.balance.netAmount).isEqualTo(10_000)
        assertThat(summary.balance.creditor?.nickname).isEqualTo("성준")
        assertThat(summary.balance.debtor?.nickname).isEqualTo("지현")
    }

    @Test
    fun `정확히 상계되면 순잔액이 0 이 된다`() {
        spend(payerId = couple.ownerId, amount = 20_000)
        spend(payerId = couple.partnerId, amount = 20_000)

        val balance = summaryService.monthly(couple.ownerId, january).balance

        assertThat(balance.netAmount).isZero()
        assertThat(balance.creditor).isNull()
        assertThat(balance.debtor).isNull()
    }

    @Test
    fun `부담 비율이 다르면 그 비율대로 잔액이 잡힌다`() {
        // 월세 100만원을 성준이 결제하되 성준이 30%만 부담
        spend(payerId = couple.ownerId, amount = 1_000_000, rate = 30, category = ExpenseCategory.RENT)

        val balance = summaryService.monthly(couple.ownerId, january).balance

        assertThat(balance.netAmount).isEqualTo(700_000)
        assertThat(balance.creditor?.nickname).isEqualTo("성준")
    }

    @Test
    fun `누가 조회해도 같은 잔액과 방향이 나온다`() {
        spend(payerId = couple.ownerId, amount = 30_000)

        val fromOwner = summaryService.monthly(couple.ownerId, january).balance
        val fromPartner = summaryService.monthly(couple.partnerId, january).balance

        assertThat(fromPartner.netAmount).isEqualTo(fromOwner.netAmount)
        assertThat(fromPartner.creditor?.userId).isEqualTo(fromOwner.creditor?.userId)
        assertThat(fromPartner.debtor?.userId).isEqualTo(fromOwner.debtor?.userId)
    }

    @Test
    fun `사람별 결제액과 부담액의 합은 총지출과 같고 순액은 서로 상쇄된다`() {
        spend(payerId = couple.ownerId, amount = 30_000)
        spend(payerId = couple.partnerId, amount = 10_000)

        val summary = summaryService.monthly(couple.ownerId, january)
        val owner = summary.members.first { it.member.userId == couple.ownerId }
        val partner = summary.members.first { it.member.userId == couple.partnerId }

        assertThat(owner.paidAmount).isEqualTo(30_000)
        assertThat(partner.paidAmount).isEqualTo(10_000)
        assertThat(owner.burdenAmount + partner.burdenAmount).isEqualTo(summary.totalAmount)
        assertThat(owner.netAmount).isEqualTo(10_000)
        assertThat(partner.netAmount).isEqualTo(-10_000)
    }

    @Test
    fun `다른 달의 지출은 섞이지 않는다`() {
        spend(payerId = couple.ownerId, amount = 30_000, month = 1)
        spend(payerId = couple.ownerId, amount = 99_000, month = 2)

        val january = summaryService.monthly(couple.ownerId, YearMonth.of(2026, 1))
        val february = summaryService.monthly(couple.ownerId, YearMonth.of(2026, 2))

        assertThat(january.totalAmount).isEqualTo(30_000)
        assertThat(february.totalAmount).isEqualTo(99_000)
    }

    @Test
    fun `월 경계의 지출도 그 달에 포함된다`() {
        spend(payerId = couple.ownerId, amount = 1_000, day = 1)
        spend(payerId = couple.ownerId, amount = 2_000, day = 31)

        assertThat(summaryService.monthly(couple.ownerId, january).totalAmount).isEqualTo(3_000)
    }

    @Test
    fun `삭제한 지출은 집계에서 빠진다`() {
        val kept = spend(payerId = couple.ownerId, amount = 30_000)
        val removed = spend(payerId = couple.ownerId, amount = 70_000)

        expenseService.delete(couple.ownerId, removed.expenseId)

        val summary = summaryService.monthly(couple.ownerId, january)
        assertThat(summary.totalAmount).isEqualTo(30_000)
        assertThat(summary.expenseCount).isEqualTo(1)
        assertThat(summary.balance.netAmount).isEqualTo(kept.partnerShare)
    }

    @Test
    fun `카테고리별 합계와 비중이 큰 순서로 나온다`() {
        spend(payerId = couple.ownerId, amount = 600_000, category = ExpenseCategory.RENT)
        spend(payerId = couple.ownerId, amount = 300_000, category = ExpenseCategory.GROCERY)
        spend(payerId = couple.partnerId, amount = 100_000, category = ExpenseCategory.DATE)

        val categories = summaryService.monthly(couple.ownerId, january).categories

        assertThat(categories.map { it.category })
            .containsExactly(ExpenseCategory.RENT, ExpenseCategory.GROCERY, ExpenseCategory.DATE)
        assertThat(categories.map { it.categoryName })
            .containsExactly("월세", "장보기", "데이트")
        assertThat(categories.map { it.ratio }).containsExactly(60.0, 30.0, 10.0)
        assertThat(categories.sumOf { it.amount }).isEqualTo(1_000_000)
    }

    @Test
    fun `나누어떨어지지 않는 금액은 1원 단위까지 보존된다`() {
        // 건별 상대 몫 5,000 / 결제자 몫 5,001 (남는 1원은 결제자가 흡수)
        spend(payerId = couple.ownerId, amount = 10_001, rate = 50)
        spend(payerId = couple.ownerId, amount = 10_001, rate = 50)
        spend(payerId = couple.ownerId, amount = 10_001, rate = 50)

        val summary = summaryService.monthly(couple.ownerId, january)
        val owner = summary.members.first { it.member.userId == couple.ownerId }
        val partner = summary.members.first { it.member.userId == couple.partnerId }

        assertThat(summary.totalAmount).isEqualTo(30_003)
        assertThat(owner.burdenAmount).isEqualTo(15_003)
        assertThat(partner.burdenAmount).isEqualTo(15_000)
        // 건별로 계산해 합친 값과 일치해야 한다 (합계를 먼저 내고 비율을 곱하면 15,001.5 가 된다)
        assertThat(summary.balance.netAmount).isEqualTo(15_000)
    }

    @Test
    fun `정산 레코드가 없으면 상태는 null 이다`() {
        assertThat(summaryService.monthly(couple.ownerId, january).settlementStatus).isNull()
    }
}

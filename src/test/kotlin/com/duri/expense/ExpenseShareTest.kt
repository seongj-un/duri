package com.duri.expense

import com.duri.couple.domain.Couple
import com.duri.expense.domain.Expense
import com.duri.expense.domain.ExpenseCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

@DisplayName("지출 부담 몫 계산")
class ExpenseShareTest {

    @ParameterizedTest(name = "{0}원을 결제자 {1}% 부담 -> 결제자 {2}원, 상대 {3}원")
    @CsvSource(
        "10000, 50, 5000, 5000",   // 반반
        "10000, 100, 10000, 0",    // 결제자가 전액 부담
        "10000, 0, 0, 10000",      // 상대가 전액 부담
        "30000, 70, 21000, 9000",  // 비대칭 부담
        "10001, 50, 5001, 5000",   // 나누어떨어지지 않으면 남는 1원은 결제자가 흡수한다
        "101, 50, 51, 50",         // 반반이어도 상대가 절반을 넘게 갚는 일은 없다
        "101, 30, 31, 70",         // 비대칭 비율에서도 나머지는 결제자에게 붙는다
        "3, 50, 2, 1",             // 최소 금액에서도 합계가 보존된다
    )
    fun `부담 몫의 합은 항상 결제 금액과 같다`(
        amount: Long,
        burdenRate: Int,
        expectedPayerShare: Long,
        expectedPartnerShare: Long,
    ) {
        val expense = expense(amount = amount, burdenRate = burdenRate)

        assertThat(expense.payerShare).isEqualTo(expectedPayerShare)
        assertThat(expense.partnerShare).isEqualTo(expectedPartnerShare)
        assertThat(expense.payerShare + expense.partnerShare).isEqualTo(amount)
    }

    @Test
    @DisplayName("나누어떨어지지 않는 1원은 결제자가 흡수한다")
    fun `나머지는 결제자 몫으로 간다`() {
        // 101원을 반반으로 나누면 정확히 50.5원씩이다. 남는 1원은 돈을 먼저 낸
        // 결제자가 떠안아야 하고, 상대가 갚을 몫이 절반을 넘어서는 안 된다.
        val expense = expense(amount = 101, burdenRate = 50)

        assertThat(expense.payerShare).isEqualTo(51)
        assertThat(expense.partnerShare).isEqualTo(50)
        assertThat(expense.partnerShare).isLessThan(expense.payerShare)
    }

    @Test
    @DisplayName("상대 몫은 상대의 명목 비율을 넘지 않는다")
    fun `상대 몫은 올림되지 않는다`() {
        // 1원부터 1000원까지 모든 금액 · 모든 비율에서, 상대가 갚을 몫은
        // 명목 비율로 계산한 실수값을 넘지 않는다(= 내림은 언제나 상대 쪽에서 일어난다).
        for (amount in 1L..1000L) {
            for (rate in 0..100) {
                val expense = expense(amount = amount, burdenRate = rate)
                val partnerNominal = amount * (100 - rate) / 100.0

                assertThat(expense.partnerShare.toDouble())
                    .`as`("amount=%d rate=%d", amount, rate)
                    .isLessThanOrEqualTo(partnerNominal)
                assertThat(expense.payerShare + expense.partnerShare)
                    .`as`("amount=%d rate=%d", amount, rate)
                    .isEqualTo(amount)
            }
        }
    }

    private fun expense(amount: Long, burdenRate: Int) = Expense(
        couple = Couple(name = "우리집", createdBy = 1L),
        payerId = 1L,
        amount = amount,
        category = ExpenseCategory.GROCERY,
        payerBurdenRate = burdenRate.toShort(),
        spentAt = LocalDate.of(2026, 1, 15),
        createdBy = 1L,
    )
}

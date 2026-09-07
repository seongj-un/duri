package com.duri.expense

import com.duri.couple.domain.Couple
import com.duri.expense.domain.Expense
import com.duri.expense.domain.ExpenseCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
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
        "10001, 50, 5000, 5001",   // 나누어떨어지지 않으면 남는 1원은 상대 몫으로 간다
        "3, 50, 1, 2",             // 최소 금액에서도 합계가 보존된다
    )
    fun `부담 몫의 합은 항상 결제 금액과 같다`(
        amount: Long,
        burdenRate: Int,
        expectedPayerShare: Long,
        expectedPartnerShare: Long,
    ) {
        val expense = Expense(
            couple = Couple(name = "우리집", createdBy = 1L),
            payerId = 1L,
            amount = amount,
            category = ExpenseCategory.GROCERY,
            payerBurdenRate = burdenRate.toShort(),
            spentAt = LocalDate.of(2026, 1, 15),
            createdBy = 1L,
        )

        assertThat(expense.payerShare).isEqualTo(expectedPayerShare)
        assertThat(expense.partnerShare).isEqualTo(expectedPartnerShare)
        assertThat(expense.payerShare + expense.partnerShare).isEqualTo(amount)
    }
}

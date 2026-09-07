package com.duri.expense

import com.duri.common.error.BusinessException
import com.duri.expense.application.CategoryTrendService
import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("월별 카테고리 추이")
class CategoryTrendServiceTest(
    private val trendService: CategoryTrendService,
    private val expenseService: ExpenseService,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
    }

    private fun spend(amount: Long, category: ExpenseCategory, year: Int, month: Int, day: Int = 15) =
        expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(
                couple.ownerId, amount, category, 50, LocalDate.of(year, month, day), null,
            ),
        )

    @Test
    fun `지출이 없는 달도 0 으로 채워 축이 끊기지 않는다`() {
        spend(30_000, ExpenseCategory.GROCERY, 2026, 1)

        val trend = trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 3)

        assertThat(trend.months).containsExactly("2025-11", "2025-12", "2026-01")
        assertThat(trend.totalByMonth).containsExactly(0, 0, 30_000)
        assertThat(trend.from).isEqualTo("2025-11")
        assertThat(trend.to).isEqualTo("2026-01")
    }

    @Test
    fun `카테고리별 월 배열이 축과 같은 길이로 온다`() {
        spend(100_000, ExpenseCategory.RENT, 2025, 12)
        spend(200_000, ExpenseCategory.RENT, 2026, 1)
        spend(50_000, ExpenseCategory.DATE, 2026, 1)

        val trend = trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 3)

        val rent = trend.categories.first { it.category == ExpenseCategory.RENT }
        assertThat(rent.monthlyAmounts).hasSameSizeAs(trend.months)
        assertThat(rent.monthlyAmounts).containsExactly(0, 100_000, 200_000)
        assertThat(rent.totalAmount).isEqualTo(300_000)
        assertThat(rent.peakMonth).isEqualTo("2026-01")
    }

    @Test
    fun `지출이 없는 카테고리는 목록에서 빠지고 많이 쓴 순으로 정렬된다`() {
        spend(100_000, ExpenseCategory.RENT, 2026, 1)
        spend(300_000, ExpenseCategory.GROCERY, 2026, 1)
        spend(50_000, ExpenseCategory.DATE, 2026, 1)

        val trend = trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 1)

        assertThat(trend.categories.map { it.category })
            .containsExactly(ExpenseCategory.GROCERY, ExpenseCategory.RENT, ExpenseCategory.DATE)
        assertThat(trend.categories).noneMatch { it.category == ExpenseCategory.TRAVEL }
    }

    @Test
    fun `평균은 지출이 없던 달까지 나눈다`() {
        spend(300_000, ExpenseCategory.GROCERY, 2026, 1)

        val trend = trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 3)

        assertThat(trend.totalAmount).isEqualTo(300_000)
        // 3개월로 나눈다. "요즘 덜 쓴다"가 평균에 드러나야 하기 때문이다.
        assertThat(trend.monthlyAverage).isEqualTo(100_000)
    }

    @Test
    fun `비중의 합은 100 에 수렴한다`() {
        spend(600_000, ExpenseCategory.RENT, 2026, 1)
        spend(300_000, ExpenseCategory.GROCERY, 2026, 1)
        spend(100_000, ExpenseCategory.DATE, 2026, 1)

        val trend = trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 1)

        assertThat(trend.categories.sumOf { it.ratio }).isEqualTo(100.0)
        assertThat(trend.categories.sumOf { it.totalAmount }).isEqualTo(trend.totalAmount)
    }

    @Test
    fun `조회 범위를 벗어난 지출은 섞이지 않는다`() {
        spend(999_000, ExpenseCategory.RENT, 2025, 10)
        spend(30_000, ExpenseCategory.RENT, 2026, 1)

        val trend = trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 3)

        assertThat(trend.totalAmount).isEqualTo(30_000)
    }

    @Test
    fun `조회 기간이 허용 범위를 벗어나면 거부한다`() {
        assertThatThrownBy { trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 0) }
            .isInstanceOf(BusinessException::class.java)
        assertThatThrownBy { trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 25) }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `지출이 하나도 없으면 빈 추이를 돌려준다`() {
        val trend = trendService.trend(couple.ownerId, YearMonth.of(2026, 1), months = 6)

        assertThat(trend.totalAmount).isZero()
        assertThat(trend.categories).isEmpty()
        assertThat(trend.totalByMonth).hasSize(6).allMatch { it == 0L }
    }
}

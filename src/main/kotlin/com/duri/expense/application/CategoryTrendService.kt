package com.duri.expense.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.application.CoupleContextLoader
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.domain.ExpenseQueryRepository
import com.duri.expense.domain.ExpenseSearchCondition
import com.duri.expense.dto.CategoryTrendResponse
import com.duri.expense.dto.CategoryTrendRow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Service
class CategoryTrendService(
    private val queryRepository: ExpenseQueryRepository,
    private val coupleContextLoader: CoupleContextLoader,
) {

    @Transactional(readOnly = true)
    fun trend(userId: Long, until: YearMonth, months: Int): CategoryTrendResponse {
        if (months !in MIN_MONTHS..MAX_MONTHS) {
            throw BusinessException(
                ErrorCode.INVALID_REQUEST,
                "조회 기간은 ${MIN_MONTHS}개월에서 ${MAX_MONTHS}개월 사이여야 합니다.",
            )
        }
        val context = coupleContextLoader.loadActive(userId)

        val start = until.minusMonths((months - 1).toLong())
        val axis = (0 until months).map { start.plusMonths(it.toLong()) }
        val axisKeys = axis.map { it.format(KEY_FORMAT) }

        val rows = queryRepository.aggregateByMonthAndCategory(
            ExpenseSearchCondition(
                coupleId = context.coupleId,
                from = start.atDay(1),
                to = until.atEndOfMonth(),
            ),
        )

        // (월, 카테고리) -> 금액. 없는 칸은 0 으로 채운다.
        val byCell = rows.associate { (it.period to it.category) to it.amount }
        val totalByMonth = axisKeys.map { key ->
            ExpenseCategory.entries.sumOf { byCell[key to it] ?: 0L }
        }
        val totalAmount = totalByMonth.sum()

        val categories = ExpenseCategory.entries
            .map { category ->
                val amounts = axisKeys.map { byCell[it to category] ?: 0L }
                val sum = amounts.sum()
                CategoryTrendRow(
                    category = category,
                    categoryName = category.displayName,
                    totalAmount = sum,
                    monthlyAmounts = amounts,
                    ratio = sum.percentageOf(totalAmount),
                    peakMonth = amounts
                        .withIndex()
                        .filter { it.value > 0 }
                        .maxByOrNull { it.value }
                        ?.let { axis[it.index].format(LABEL_FORMAT) },
                )
            }
            .filter { it.totalAmount > 0 }
            .sortedByDescending { it.totalAmount }

        return CategoryTrendResponse(
            from = start.format(LABEL_FORMAT),
            to = until.format(LABEL_FORMAT),
            months = axis.map { it.format(LABEL_FORMAT) },
            totalByMonth = totalByMonth,
            totalAmount = totalAmount,
            // 지출이 없던 달도 나눗셈에 포함한다. 그래야 "요즘 덜 쓴다"가 평균에 드러난다.
            monthlyAverage = if (months == 0) 0 else totalAmount / months,
            categories = categories,
        )
    }

    private fun Long.percentageOf(total: Long): Double =
        if (total <= 0) 0.0
        else BigDecimal(this * 100).divide(BigDecimal(total), 1, RoundingMode.HALF_UP).toDouble()

    companion object {
        const val MIN_MONTHS = 1
        const val MAX_MONTHS = 24
        const val DEFAULT_MONTHS = 6

        /** DB 의 to_char(spent_at,'YYYYMM') 과 맞춘 조회 키. */
        private val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMM")

        /** 응답에 나가는 표시 형식. */
        private val LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM")
    }
}

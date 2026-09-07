package com.duri.expense.dto

import com.duri.expense.domain.ExpenseCategory

/**
 * 최근 몇 달의 지출 추이.
 *
 * [months] 와 같은 길이·순서의 배열로 값을 내려 그래프에 그대로 꽂을 수 있게 한다.
 * 지출이 하나도 없는 달도 0 으로 채워 축이 끊기지 않게 한다.
 */
data class CategoryTrendResponse(
    val from: String,
    val to: String,
    /** ["2026-04", "2026-05", ...] 오래된 달부터. */
    val months: List<String>,
    val totalByMonth: List<Long>,
    val totalAmount: Long,
    /** 지출이 없던 달도 포함해 나눈 평균. */
    val monthlyAverage: Long,
    val categories: List<CategoryTrendRow>,
)

data class CategoryTrendRow(
    val category: ExpenseCategory,
    val categoryName: String,
    val totalAmount: Long,
    /** months 와 같은 순서·길이. */
    val monthlyAmounts: List<Long>,
    /** 전체 지출 대비 비중(%). 소수 첫째 자리까지. */
    val ratio: Double,
    /** 이 카테고리를 가장 많이 쓴 달. 지출이 없으면 null. */
    val peakMonth: String?,
)

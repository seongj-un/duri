package com.duri.expense.api

import com.duri.common.web.CurrentUserId
import com.duri.expense.application.CategoryTrendService
import com.duri.expense.application.MonthlySummaryService
import com.duri.expense.dto.CategoryTrendResponse
import com.duri.expense.dto.MonthlySummaryResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.YearMonth

@RestController
@RequestMapping("/api/v1/summaries")
class SummaryController(
    private val monthlySummaryService: MonthlySummaryService,
    private val categoryTrendService: CategoryTrendService,
    private val clock: Clock,
) {

    /** 월별 뷰 한 화면 분량: 합계 · 사람별 부담 · 카테고리 비중 · 순잔액. */
    @GetMapping("/monthly")
    fun monthly(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") period: YearMonth?,
    ): MonthlySummaryResponse =
        monthlySummaryService.monthly(userId, period ?: YearMonth.now(clock))

    /** 최근 몇 달의 카테고리별 지출 추이. 그래프에 그대로 꽂을 수 있는 배열로 준다. */
    @GetMapping("/trend")
    fun trend(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") until: YearMonth?,
        @RequestParam(required = false) months: Int?,
    ): CategoryTrendResponse = categoryTrendService.trend(
        userId = userId,
        until = until ?: YearMonth.now(clock),
        months = months ?: CategoryTrendService.DEFAULT_MONTHS,
    )
}

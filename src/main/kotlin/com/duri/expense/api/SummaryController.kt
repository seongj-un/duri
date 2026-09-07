package com.duri.expense.api

import com.duri.common.web.CurrentUserId
import com.duri.expense.application.MonthlySummaryService
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
    private val clock: Clock,
) {

    /** 월별 뷰 한 화면 분량: 합계 · 사람별 부담 · 카테고리 비중 · 순잔액. */
    @GetMapping("/monthly")
    fun monthly(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") period: YearMonth?,
    ): MonthlySummaryResponse =
        monthlySummaryService.monthly(userId, period ?: YearMonth.now(clock))
}

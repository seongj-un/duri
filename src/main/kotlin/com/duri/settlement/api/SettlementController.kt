package com.duri.settlement.api

import com.duri.common.web.CurrentUserId
import com.duri.settlement.application.SettlementService
import com.duri.settlement.dto.SettlementHistoryResponse
import com.duri.settlement.dto.SettlementResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.YearMonth

@RestController
@RequestMapping("/api/v1/settlements")
class SettlementController(
    private val settlementService: SettlementService,
    private val clock: Clock,
) {

    /** 확정된 정산 이력. */
    @GetMapping
    fun history(@CurrentUserId userId: Long): List<SettlementHistoryResponse> =
        settlementService.history(userId)

    /** 특정 달의 정산 현황. 확정 전이면 실시간 계산 결과를 보여준다. */
    @GetMapping("/{period}")
    fun get(
        @CurrentUserId userId: Long,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM") period: YearMonth,
    ): SettlementResponse = settlementService.get(userId, period)

    /**
     * 정산 확정. 여러 번 눌러도 결과가 같다.
     * period 를 생략하면 지난달을 확정한다(월초에 지난달을 마감하는 흐름).
     */
    @PostMapping("/confirm")
    fun confirmLastMonth(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") period: YearMonth?,
    ): SettlementResponse =
        settlementService.confirm(userId, period ?: YearMonth.now(clock).minusMonths(1))

    @PostMapping("/{period}/confirm")
    fun confirm(
        @CurrentUserId userId: Long,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM") period: YearMonth,
    ): SettlementResponse = settlementService.confirm(userId, period)
}

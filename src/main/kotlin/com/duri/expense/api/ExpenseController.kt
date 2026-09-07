package com.duri.expense.api

import com.duri.common.web.CurrentUserId
import com.duri.expense.application.ExpenseQueryService
import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.expense.dto.ExpensePageResponse
import com.duri.expense.dto.ExpenseResponse
import com.duri.expense.dto.ExpenseUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.YearMonth

@RestController
@RequestMapping("/api/v1/expenses")
class ExpenseController(
    private val expenseService: ExpenseService,
    private val expenseQueryService: ExpenseQueryService,
    private val clock: Clock,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: ExpenseCreateRequest,
    ): ExpenseResponse = expenseService.create(userId, request)

    /** 월별 지출 목록. period 를 생략하면 이번 달을 본다. */
    @GetMapping
    fun list(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") period: YearMonth?,
        @RequestParam(required = false) category: ExpenseCategory?,
        @RequestParam(required = false) payerId: Long?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ExpensePageResponse =
        expenseQueryService.list(userId, period ?: currentPeriod(), category, payerId, pageable)

    @GetMapping("/{expenseId}")
    fun get(
        @CurrentUserId userId: Long,
        @PathVariable expenseId: Long,
    ): ExpenseResponse = expenseService.get(userId, expenseId)

    @PatchMapping("/{expenseId}")
    fun update(
        @CurrentUserId userId: Long,
        @PathVariable expenseId: Long,
        @Valid @RequestBody request: ExpenseUpdateRequest,
    ): ExpenseResponse = expenseService.update(userId, expenseId, request)

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable expenseId: Long,
    ) = expenseService.delete(userId, expenseId)

    private fun currentPeriod() = YearMonth.now(clock)
}

package com.duri.expense.api

import com.duri.common.web.CurrentUserId
import com.duri.expense.application.RecurringExpenseService
import com.duri.expense.dto.RecurringExpenseCreateRequest
import com.duri.expense.dto.RecurringExpenseResponse
import com.duri.expense.dto.RecurringExpenseUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 월세·공과금·구독처럼 매달 반복되는 지출의 정의. 실제 지출은 스케줄러가 만든다. */
@RestController
@RequestMapping("/api/v1/recurring-expenses")
class RecurringExpenseController(
    private val recurringExpenseService: RecurringExpenseService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: RecurringExpenseCreateRequest,
    ): RecurringExpenseResponse = recurringExpenseService.create(userId, request)

    @GetMapping
    fun list(@CurrentUserId userId: Long): List<RecurringExpenseResponse> =
        recurringExpenseService.list(userId)

    @PatchMapping("/{recurringExpenseId}")
    fun update(
        @CurrentUserId userId: Long,
        @PathVariable recurringExpenseId: Long,
        @Valid @RequestBody request: RecurringExpenseUpdateRequest,
    ): RecurringExpenseResponse = recurringExpenseService.update(userId, recurringExpenseId, request)

    /** 중지. 이미 만들어진 지출은 그대로 두고 다음 달부터 만들지 않는다. */
    @PostMapping("/{recurringExpenseId}/deactivate")
    fun deactivate(
        @CurrentUserId userId: Long,
        @PathVariable recurringExpenseId: Long,
    ): RecurringExpenseResponse = recurringExpenseService.setActive(userId, recurringExpenseId, false)

    @PostMapping("/{recurringExpenseId}/activate")
    fun activate(
        @CurrentUserId userId: Long,
        @PathVariable recurringExpenseId: Long,
    ): RecurringExpenseResponse = recurringExpenseService.setActive(userId, recurringExpenseId, true)

    /** 아직 아무것도 만들어지지 않은 정의만 지울 수 있다. 그 외에는 중지를 쓴다. */
    @DeleteMapping("/{recurringExpenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUserId userId: Long,
        @PathVariable recurringExpenseId: Long,
    ) = recurringExpenseService.delete(userId, recurringExpenseId)
}

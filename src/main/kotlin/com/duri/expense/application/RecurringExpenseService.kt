package com.duri.expense.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.application.CoupleContext
import com.duri.couple.application.CoupleContextLoader
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.domain.RecurringExpense
import com.duri.expense.domain.RecurringExpenseRepository
import com.duri.expense.dto.RecurringExpenseCreateRequest
import com.duri.expense.dto.RecurringExpenseResponse
import com.duri.expense.dto.RecurringExpenseUpdateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class RecurringExpenseService(
    private val recurringExpenseRepository: RecurringExpenseRepository,
    private val expenseRepository: ExpenseRepository,
    private val coupleContextLoader: CoupleContextLoader,
    private val burdenPresetService: BurdenPresetService,
    private val clock: Clock,
) {

    @Transactional
    fun create(userId: Long, request: RecurringExpenseCreateRequest): RecurringExpenseResponse {
        val context = coupleContextLoader.loadActive(userId)
        val payerId = requireNotNull(request.payerId)
        val category = requireNotNull(request.category)
        context.requirePayer(payerId)

        val today = today()
        val startsOn = request.startsOn ?: today
        val endsOn = request.endsOn
        if (endsOn != null && endsOn.isBefore(startsOn)) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "종료일은 시작일보다 앞설 수 없습니다.")
        }

        // 프리셋은 등록 시점에 확정해 저장한다. 나중에 프리셋을 바꿔도
        // 이미 등록해 둔 월세의 부담 비율이 조용히 달라지면 안 되기 때문이다.
        val burdenRate = request.payerBurdenRate?.toShort()
            ?: burdenPresetService.payerBurdenRateFor(context, category, payerId)

        val recurring = recurringExpenseRepository.save(
            RecurringExpense(
                couple = context.couple,
                payerId = payerId,
                title = requireNotNull(request.title).trim(),
                amount = requireNotNull(request.amount),
                category = category,
                payerBurdenRate = burdenRate,
                dayOfMonth = requireNotNull(request.dayOfMonth).toShort(),
                startsOn = startsOn,
                memo = request.memo?.takeIf { it.isNotBlank() },
                endsOn = endsOn,
                createdBy = userId,
            ),
        )
        return RecurringExpenseResponse.of(recurring, context.memberRefOf(payerId), today)
    }

    @Transactional(readOnly = true)
    fun list(userId: Long): List<RecurringExpenseResponse> {
        val context = coupleContextLoader.loadActive(userId)
        val today = today()
        return recurringExpenseRepository
            .findAllByCoupleIdOrderByDayOfMonthAscIdAsc(context.coupleId)
            .map { RecurringExpenseResponse.of(it, context.memberRefOf(it.payerId), today) }
    }

    @Transactional
    fun update(
        userId: Long,
        recurringExpenseId: Long,
        request: RecurringExpenseUpdateRequest,
    ): RecurringExpenseResponse {
        val context = coupleContextLoader.loadActive(userId)
        val recurring = find(context, recurringExpenseId)

        val payerId = request.payerId ?: recurring.payerId
        context.requirePayer(payerId)

        recurring.update(
            payerId = payerId,
            title = request.title?.trim()?.ifBlank { recurring.title } ?: recurring.title,
            amount = request.amount ?: recurring.amount,
            category = request.category ?: recurring.category,
            payerBurdenRate = request.payerBurdenRate?.toShort() ?: recurring.payerBurdenRate,
            dayOfMonth = request.dayOfMonth?.toShort() ?: recurring.dayOfMonth,
            startsOn = request.startsOn ?: recurring.startsOn,
            endsOn = request.endsOn ?: recurring.endsOn,
            memo = if (request.memo != null) request.memo.takeIf { it.isNotBlank() } else recurring.memo,
        )
        return RecurringExpenseResponse.of(recurring, context.memberRefOf(payerId), today())
    }

    /** 잠시 멈춤. 이미 만들어진 지출은 그대로 둔다. */
    @Transactional
    fun setActive(userId: Long, recurringExpenseId: Long, active: Boolean): RecurringExpenseResponse {
        val context = coupleContextLoader.loadActive(userId)
        val recurring = find(context, recurringExpenseId)

        if (active) recurring.activate() else recurring.deactivate()

        return RecurringExpenseResponse.of(recurring, context.memberRefOf(recurring.payerId), today())
    }

    /**
     * 정의를 지운다.
     *
     * 이미 이 정의로 만들어진 지출이 있으면 지우지 않는다. 지난달 월세가 정산의 근거인데
     * 정의가 사라지면 그 지출이 어디서 왔는지 알 수 없게 되기 때문이다.
     * 그 경우엔 중지(deactivate)를 쓰라고 알려준다.
     */
    @Transactional
    fun delete(userId: Long, recurringExpenseId: Long) {
        val context = coupleContextLoader.loadActive(userId)
        val recurring = find(context, recurringExpenseId)

        if (expenseRepository.existsByRecurringExpenseId(recurring.requiredId)) {
            throw BusinessException(ErrorCode.RECURRING_EXPENSE_IN_USE)
        }
        recurringExpenseRepository.delete(recurring)
    }

    private fun find(context: CoupleContext, recurringExpenseId: Long): RecurringExpense =
        recurringExpenseRepository.findByIdAndCoupleId(recurringExpenseId, context.coupleId)
            ?: throw BusinessException(ErrorCode.RECURRING_EXPENSE_NOT_FOUND)

    private fun today(): LocalDate = LocalDate.now(clock)
}

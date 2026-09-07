package com.duri.expense.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.application.CoupleContext
import com.duri.couple.application.CoupleContextLoader
import com.duri.expense.domain.Expense
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.expense.dto.ExpenseResponse
import com.duri.expense.dto.ExpenseUpdateRequest
import com.duri.expense.dto.MemberRef
import com.duri.settlement.domain.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

@Service
class ExpenseService(
    private val expenseRepository: ExpenseRepository,
    private val settlementRepository: SettlementRepository,
    private val coupleContextLoader: CoupleContextLoader,
    private val burdenPresetService: BurdenPresetService,
    private val clock: Clock,
) {

    @Transactional
    fun create(userId: Long, request: ExpenseCreateRequest): ExpenseResponse {
        val context = coupleContextLoader.loadActive(userId)
        val payerId = requireNotNull(request.payerId)
        val spentAt = requireNotNull(request.spentAt)

        context.requirePayer(payerId)
        requirePeriodOpen(context.coupleId, spentAt)

        val category = requireNotNull(request.category)
        // 비율을 안 보냈으면 카테고리 프리셋을 따른다
        val burdenRate = request.payerBurdenRate?.toShort()
            ?: burdenPresetService.payerBurdenRateFor(context, category, payerId)

        val expense = expenseRepository.save(
            Expense(
                couple = context.couple,
                payerId = payerId,
                amount = requireNotNull(request.amount),
                category = category,
                payerBurdenRate = burdenRate,
                spentAt = spentAt,
                memo = request.memo?.takeIf { it.isNotBlank() },
                createdBy = userId,
            ),
        )
        return ExpenseResponse.of(expense, context.memberRefOf(payerId))
    }

    @Transactional
    fun update(userId: Long, expenseId: Long, request: ExpenseUpdateRequest): ExpenseResponse {
        val context = coupleContextLoader.loadActive(userId)
        val expense = findEditable(context, expenseId)

        val payerId = request.payerId ?: expense.payerId
        val spentAt = request.spentAt ?: expense.spentAt

        context.requirePayer(payerId)
        // 달을 옮기는 수정이면 옮겨 나가는 달과 들어가는 달 양쪽이 모두 열려 있어야 한다
        requirePeriodOpen(context.coupleId, expense.spentAt)
        if (spentAt != expense.spentAt) requirePeriodOpen(context.coupleId, spentAt)

        expense.update(
            payerId = payerId,
            amount = request.amount ?: expense.amount,
            category = request.category ?: expense.category,
            payerBurdenRate = request.payerBurdenRate?.toShort() ?: expense.payerBurdenRate,
            spentAt = spentAt,
            memo = if (request.memo != null) request.memo.takeIf { it.isNotBlank() } else expense.memo,
        )
        return ExpenseResponse.of(expense, context.memberRefOf(expense.payerId))
    }

    @Transactional
    fun delete(userId: Long, expenseId: Long) {
        val context = coupleContextLoader.loadActive(userId)
        val expense = findEditable(context, expenseId)
        requirePeriodOpen(context.coupleId, expense.spentAt)
        expense.softDelete(clock.instant())
    }

    @Transactional(readOnly = true)
    fun get(userId: Long, expenseId: Long): ExpenseResponse {
        val context = coupleContextLoader.loadActive(userId)
        val expense = expenseRepository.findByIdAndCoupleId(expenseId, context.coupleId)
            ?.takeIf { !it.isDeleted }
            ?: throw BusinessException(ErrorCode.EXPENSE_NOT_FOUND)
        return ExpenseResponse.of(expense, context.memberRefOf(expense.payerId))
    }

    private fun findEditable(context: CoupleContext, expenseId: Long): Expense {
        val expense = expenseRepository.findByIdAndCoupleId(expenseId, context.coupleId)
            ?: throw BusinessException(ErrorCode.EXPENSE_NOT_FOUND)
        expense.requireEditable()
        return expense
    }

    /**
     * 확정된 달에는 지출을 넣거나 뺄 수 없다.
     * 개별 지출의 잠금(settlement_id)과 별개로, 확정 이후 새로 끼워 넣는 것을 막는다.
     */
    private fun requirePeriodOpen(coupleId: Long, date: LocalDate) {
        val settlement = settlementRepository.findByCoupleIdAndPeriod(coupleId, YearMonth.from(date))
        if (settlement != null && settlement.isConfirmed) {
            throw BusinessException(ErrorCode.PERIOD_ALREADY_SETTLED)
        }
    }
}

/** 커플 문맥에서 표시용 사용자 정보를 뽑는다. */
internal fun CoupleContext.memberRefOf(userId: Long): MemberRef =
    memberOf(userId).user.let { MemberRef(it.requiredId, it.nickname, it.profileImageUrl) }

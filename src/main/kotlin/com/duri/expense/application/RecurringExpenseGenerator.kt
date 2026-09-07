package com.duri.expense.application

import com.duri.expense.domain.Expense
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.domain.RecurringExpenseRepository
import com.duri.realtime.application.CoupleEventPublisher
import com.duri.realtime.domain.CoupleEventType
import com.duri.settlement.domain.SettlementRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

/**
 * 반복지출 정의 하나로 실제 지출 한 건을 만든다.
 *
 * 건마다 독립된 트랜잭션이다. 한 커플의 월세 생성이 실패해도
 * 다른 커플의 구독료 생성까지 함께 롤백되면 안 되기 때문이다.
 */
@Component
class RecurringExpenseGenerator(
    private val recurringExpenseRepository: RecurringExpenseRepository,
    private val expenseRepository: ExpenseRepository,
    private val settlementRepository: SettlementRepository,
    private val eventPublisher: CoupleEventPublisher,
) {

    @Transactional
    fun generate(recurringExpenseId: Long, today: LocalDate): GenerationOutcome {
        val recurring = recurringExpenseRepository.findById(recurringExpenseId).orElse(null)
            ?: return GenerationOutcome.NOT_FOUND

        if (!recurring.isDueOn(today)) return GenerationOutcome.NOT_DUE

        val couple = recurring.couple
        // 헤어졌거나 아직 파트너가 없는 space 에 지출을 만들지 않는다
        if (!couple.isActive) return GenerationOutcome.COUPLE_INACTIVE

        val period = YearMonth.from(today)

        // 이미 이번 달치를 만들었으면 넘어간다.
        // 사용자가 지운 건도 "만들어진 것"으로 보므로 지운 지출이 되살아나지 않는다.
        if (
            expenseRepository.existsByRecurringExpenseIdAndSpentAtBetween(
                recurring.requiredId, period.atDay(1), period.atEndOfMonth(),
            )
        ) {
            return GenerationOutcome.ALREADY_GENERATED
        }

        // 확정된 달에 뒤늦게 끼워 넣으면 합의된 정산 금액이 흔들린다
        if (settlementRepository.findByCoupleIdAndPeriod(couple.requiredId, period)?.isConfirmed == true) {
            return GenerationOutcome.PERIOD_SETTLED
        }

        val created = expenseRepository.save(
            Expense(
                couple = couple,
                payerId = recurring.payerId,
                amount = recurring.amount,
                category = recurring.category,
                payerBurdenRate = recurring.payerBurdenRate,
                spentAt = recurring.dueDateIn(period),
                memo = recurring.memo ?: recurring.title,
                createdBy = recurring.createdBy,
                recurringExpenseId = recurring.requiredId,
            ),
        )
        eventPublisher.publish(
            type = CoupleEventType.RECURRING_EXPENSE_GENERATED,
            coupleId = couple.requiredId,
            resourceId = created.requiredId,
            period = period,
        )
        return GenerationOutcome.CREATED
    }
}

enum class GenerationOutcome {
    CREATED,
    ALREADY_GENERATED,
    NOT_DUE,
    PERIOD_SETTLED,
    COUPLE_INACTIVE,
    NOT_FOUND,
    FAILED,
}

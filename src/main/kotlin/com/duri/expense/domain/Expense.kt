package com.duri.expense.domain

import com.duri.common.entity.BaseTimeEntity
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.domain.Couple
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * 지출 한 건.
 *
 * 금액은 원 단위 정수로만 다룬다. 먼저 [partnerShare] 를 내림으로 구하고
 * 남는 1원은 [payerShare] 로 몰아, 돈을 먼저 낸 결제자가 흡수하게 한다.
 * 반대로 결제자 몫을 먼저 내림하면 "반반" 인데도 상대가 절반을 넘게 갚는다.
 */
@Entity
@Table(name = "expenses")
class Expense(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "couple_id", nullable = false, updatable = false)
    val couple: Couple,

    @Column(name = "payer_id", nullable = false)
    var payerId: Long,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: ExpenseCategory,

    @Column(name = "payer_burden_rate", nullable = false)
    var payerBurdenRate: Short,

    @Column(name = "spent_at", nullable = false)
    var spentAt: LocalDate,

    @Column(name = "memo", length = 255)
    var memo: String? = null,

    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: Long,

    /** 반복지출에서 자동 생성된 건이면 그 정의를 가리킨다. 사람이 넣은 건은 null. */
    @Column(name = "recurring_expense_id", updatable = false)
    val recurringExpenseId: Long? = null,
) : BaseTimeEntity() {

    /** 확정된 정산에 귀속되면 채워지고, 그 뒤로는 수정·삭제가 잠긴다. */
    @Column(name = "settlement_id")
    var settlementId: Long? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    val isLocked: Boolean get() = settlementId != null
    val isDeleted: Boolean get() = deletedAt != null
    val isGenerated: Boolean get() = recurringExpenseId != null

    /** 상대가 결제자에게 갚아야 할 몫. 내림이므로 명목 비율을 넘지 않는다. */
    val partnerShare: Long get() = amount * (HUNDRED - payerBurdenRate) / HUNDRED

    /** 결제자가 실제로 부담해야 할 몫. 나누어떨어지지 않고 남는 1원은 결제자가 부담한다. */
    val payerShare: Long get() = amount - partnerShare

    fun lockTo(settlementId: Long) {
        this.settlementId = settlementId
    }

    /**
     * 확정된 정산에 들어간 지출은 금액이 이미 합의된 값이므로 되돌릴 수 없다.
     * 삭제된 건은 존재하지 않는 것으로 다룬다.
     */
    fun requireEditable() {
        if (isDeleted) throw BusinessException(ErrorCode.EXPENSE_NOT_FOUND)
        if (isLocked) throw BusinessException(ErrorCode.EXPENSE_LOCKED)
    }

    fun update(
        payerId: Long,
        amount: Long,
        category: ExpenseCategory,
        payerBurdenRate: Short,
        spentAt: LocalDate,
        memo: String?,
    ) {
        requireEditable()
        require(amount > 0) { "금액은 0보다 커야 합니다." }
        require(payerBurdenRate in 0..100) { "부담 비율은 0에서 100 사이여야 합니다." }

        this.payerId = payerId
        this.amount = amount
        this.category = category
        this.payerBurdenRate = payerBurdenRate
        this.spentAt = spentAt
        this.memo = memo
    }

    fun softDelete(now: Instant) {
        requireEditable()
        deletedAt = now
    }

    private companion object {
        const val HUNDRED = 100
    }
}

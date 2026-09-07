package com.duri.expense.domain

import com.duri.common.entity.BaseTimeEntity
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
 * 금액은 원 단위 정수로만 다룬다. 부담 비율을 곱한 뒤 남는 1원은
 * [partnerShare] 에서 내림 처리되어 결제자가 흡수한다.
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

    /** 결제자가 실제로 부담해야 할 몫. */
    val payerShare: Long get() = amount * payerBurdenRate / HUNDRED

    /** 상대가 결제자에게 갚아야 할 몫. 나머지 1원은 결제자가 부담한다. */
    val partnerShare: Long get() = amount - payerShare

    fun lockTo(settlementId: Long) {
        this.settlementId = settlementId
    }

    fun softDelete(now: Instant) {
        if (deletedAt == null) deletedAt = now
    }

    private companion object {
        const val HUNDRED = 100
    }
}

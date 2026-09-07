package com.duri.settlement.domain

import com.duri.common.entity.BaseTimeEntity
import com.duri.couple.domain.Couple
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.YearMonth

/**
 * 한 커플의 한 달치 정산.
 *
 * (couple_id, period) 유니크 제약이 "같은 달을 두 번 확정할 수 없다"를 보장하므로
 * 확정 API 는 이 제약 위에서 멱등하게 동작한다.
 */
@Entity
@Table(name = "settlements")
class Settlement(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "couple_id", nullable = false, updatable = false)
    val couple: Couple,

    @Convert(converter = YearMonthConverter::class)
    @Column(name = "period", nullable = false, length = 6, updatable = false)
    val period: YearMonth,
) : BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: SettlementStatus = SettlementStatus.OPEN
        protected set

    /** 항상 0 이상. 방향은 creditor/debtor 로 표현한다. */
    @Column(name = "net_amount", nullable = false)
    var netAmount: Long = 0
        protected set

    /** 받을 사람. netAmount 가 0 이면 null. */
    @Column(name = "creditor_id")
    var creditorId: Long? = null
        protected set

    /** 보낼 사람. netAmount 가 0 이면 null. */
    @Column(name = "debtor_id")
    var debtorId: Long? = null
        protected set

    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null
        protected set

    @Column(name = "confirmed_by")
    var confirmedBy: Long? = null
        protected set

    val isConfirmed: Boolean get() = status == SettlementStatus.CONFIRMED

    /**
     * 순잔액을 갱신한다.
     * [balance] 는 memberA 기준 잔액(양수면 memberA 가 받을 돈)이다.
     */
    fun updateBalance(memberAId: Long, memberBId: Long, balance: Long) {
        check(!isConfirmed) { "확정된 정산은 금액을 바꿀 수 없습니다." }
        netAmount = kotlin.math.abs(balance)
        when {
            balance > 0 -> { creditorId = memberAId; debtorId = memberBId }
            balance < 0 -> { creditorId = memberBId; debtorId = memberAId }
            else -> { creditorId = null; debtorId = null }
        }
    }

    fun confirm(confirmedBy: Long, now: Instant) {
        check(!isConfirmed) { "이미 확정된 정산입니다." }
        status = SettlementStatus.CONFIRMED
        this.confirmedBy = confirmedBy
        confirmedAt = now
    }
}

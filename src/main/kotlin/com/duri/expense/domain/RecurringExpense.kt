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
import java.time.LocalDate
import java.time.YearMonth

/**
 * 월세·공과금·구독처럼 매달 반복되는 지출의 "정의".
 *
 * 이 엔티티 자체는 돈이 아니다. 스케줄러가 발생일이 되면 이걸 보고 실제 [Expense] 를 만든다.
 * 그래야 자동 생성된 건도 사람이 넣은 건과 똑같이 수정·삭제할 수 있고,
 * 정의를 나중에 바꿔도 이미 지나간 달의 금액이 소급해서 흔들리지 않는다.
 */
@Entity
@Table(name = "recurring_expenses")
class RecurringExpense(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "couple_id", nullable = false, updatable = false)
    val couple: Couple,

    @Column(name = "payer_id", nullable = false)
    var payerId: Long,

    @Column(name = "title", nullable = false, length = 50)
    var title: String,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: ExpenseCategory,

    @Column(name = "payer_burden_rate", nullable = false)
    var payerBurdenRate: Short,

    @Column(name = "day_of_month", nullable = false)
    var dayOfMonth: Short,

    @Column(name = "starts_on", nullable = false)
    var startsOn: LocalDate,

    @Column(name = "memo", length = 255)
    var memo: String? = null,

    @Column(name = "ends_on")
    var endsOn: LocalDate? = null,

    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: Long,
) : BaseTimeEntity() {

    @Column(name = "active", nullable = false)
    var active: Boolean = true
        protected set

    /** 해당 월의 발생일. day_of_month 를 1~28 로 제한해 두어 없는 날짜가 나오지 않는다. */
    fun dueDateIn(period: YearMonth): LocalDate = period.atDay(dayOfMonth.toInt())

    /**
     * [today] 기준으로 이번 달치를 만들어야 하는지.
     *
     * 발생일이 지났는데 아직 안 만들어졌으면 만든다. 서버가 발생일에 꺼져 있었더라도
     * 다음 실행 때 따라잡기 위해서다. 실제 중복은 DB 유니크 인덱스가 막는다.
     */
    fun isDueOn(today: LocalDate): Boolean {
        if (!active) return false
        val dueDate = dueDateIn(YearMonth.from(today))
        if (dueDate.isAfter(today)) return false
        if (dueDate.isBefore(startsOn)) return false
        return endsOn?.let { !dueDate.isAfter(it) } ?: true
    }

    /**
     * [today] 다음으로 실제 지출이 만들어질 날. 더 만들 것이 없으면 null.
     * 오늘이 발생일이고 아직 안 만들어졌다면 오늘이 답이다.
     */
    fun nextDueDateFrom(today: LocalDate): LocalDate? {
        if (!active) return null

        val candidates = listOf(
            dueDateIn(YearMonth.from(today)),
            dueDateIn(YearMonth.from(today).plusMonths(1)),
        )
        return candidates
            .firstOrNull { !it.isBefore(today) && !it.isBefore(startsOn) }
            ?.takeIf { candidate -> endsOn?.let { !candidate.isAfter(it) } ?: true }
    }

    fun activate() {
        active = true
    }

    fun deactivate() {
        active = false
    }

    fun update(
        payerId: Long,
        title: String,
        amount: Long,
        category: ExpenseCategory,
        payerBurdenRate: Short,
        dayOfMonth: Short,
        startsOn: LocalDate,
        endsOn: LocalDate?,
        memo: String?,
    ) {
        require(amount > 0) { "금액은 0보다 커야 합니다." }
        require(payerBurdenRate in 0..100) { "부담 비율은 0에서 100 사이여야 합니다." }
        require(dayOfMonth in MIN_DAY..MAX_DAY) { "발생일은 ${MIN_DAY}일부터 ${MAX_DAY}일 사이여야 합니다." }
        require(endsOn == null || !endsOn.isBefore(startsOn)) { "종료일은 시작일보다 앞설 수 없습니다." }

        this.payerId = payerId
        this.title = title
        this.amount = amount
        this.category = category
        this.payerBurdenRate = payerBurdenRate
        this.dayOfMonth = dayOfMonth
        this.startsOn = startsOn
        this.endsOn = endsOn
        this.memo = memo
    }

    companion object {
        const val MIN_DAY: Short = 1

        /** 29~31일은 없는 달이 있어 제외한다. */
        const val MAX_DAY: Short = 28
    }
}

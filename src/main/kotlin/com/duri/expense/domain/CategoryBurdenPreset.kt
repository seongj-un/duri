package com.duri.expense.domain

import com.duri.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * 카테고리마다 미리 정해 둔 부담 비율.
 *
 * 비율을 "결제자 기준"이 아니라 **사람 기준**으로 저장한다.
 * 월세를 성준 30 / 지현 70 으로 정했다면, 이번 달 카드가 누구 것이든 부담은 그대로여야 한다.
 * 결제자 기준으로 저장하면 결제자가 바뀔 때마다 부담이 뒤집힌다.
 *
 * 커플은 자리가 1번·2번 둘뿐이므로 1번의 비율만 저장하면 2번은 그 나머지다.
 */
@Entity
@Table(name = "category_burden_presets")
class CategoryBurdenPreset(
    @Column(name = "couple_id", nullable = false, updatable = false)
    val coupleId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30, updatable = false)
    val category: ExpenseCategory,

    @Column(name = "member1_burden_rate", nullable = false)
    var member1BurdenRate: Short,
) : BaseTimeEntity() {

    fun changeRate(member1BurdenRate: Short) {
        require(member1BurdenRate in 0..100) { "부담 비율은 0에서 100 사이여야 합니다." }
        this.member1BurdenRate = member1BurdenRate
    }

    val member2BurdenRate: Short get() = (FULL - member1BurdenRate).toShort()

    companion object {
        const val FULL: Short = 100
        const val DEFAULT_RATE: Short = 50
    }
}

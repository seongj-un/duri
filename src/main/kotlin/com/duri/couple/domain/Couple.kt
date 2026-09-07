package com.duri.couple.domain

import com.duri.common.entity.BaseTimeEntity
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * 커플 공유 space. 구성원은 정확히 2명이며, 그 제약은 couple_members 의
 * (couple_id, member_no) 유니크 + member_no IN (1,2) 로 DB 가 최종 보장한다.
 */
@Entity
@Table(name = "couples")
class Couple(
    @Column(name = "name", nullable = false, length = 50)
    var name: String,

    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: Long,
) : BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CoupleStatus = CoupleStatus.PENDING
        protected set

    @Column(name = "settlement_day", nullable = false)
    var settlementDay: Short = DEFAULT_SETTLEMENT_DAY
        protected set

    val isActive: Boolean get() = status == CoupleStatus.ACTIVE

    /** 파트너가 초대를 수락한 순간 호출된다. */
    fun activate() {
        if (status == CoupleStatus.ACTIVE) throw BusinessException(ErrorCode.COUPLE_ALREADY_FULL)
        if (status == CoupleStatus.DISBANDED) throw BusinessException(ErrorCode.COUPLE_NOT_FOUND)
        status = CoupleStatus.ACTIVE
    }

    fun rename(name: String) {
        require(name.isNotBlank()) { "커플 space 이름은 비워둘 수 없습니다." }
        this.name = name.trim()
    }

    fun changeSettlementDay(day: Int) {
        require(day in MIN_SETTLEMENT_DAY..MAX_SETTLEMENT_DAY) {
            "정산 기준일은 ${MIN_SETTLEMENT_DAY}일부터 ${MAX_SETTLEMENT_DAY}일 사이여야 합니다."
        }
        settlementDay = day.toShort()
    }

    /** 지출 기록·정산은 두 사람이 연결된 뒤에만 가능하다. */
    fun requireActive() {
        if (!isActive) throw BusinessException(ErrorCode.COUPLE_NOT_ACTIVE)
    }

    companion object {
        const val DEFAULT_SETTLEMENT_DAY: Short = 1
        const val MIN_SETTLEMENT_DAY = 1

        /** 29~31일은 없는 달이 있어 정산 기준일에서 제외한다. */
        const val MAX_SETTLEMENT_DAY = 28
    }
}

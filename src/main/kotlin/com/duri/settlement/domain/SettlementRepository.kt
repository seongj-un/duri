package com.duri.settlement.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.YearMonth

interface SettlementRepository : JpaRepository<Settlement, Long> {

    fun findByCoupleIdAndPeriod(coupleId: Long, period: YearMonth): Settlement?

    /** period 는 YYYYMM 고정폭이라 문자열 정렬이 곧 시간순이다. */
    fun findAllByCoupleIdOrderByPeriodDesc(coupleId: Long): List<Settlement>
}

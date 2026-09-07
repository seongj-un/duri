package com.duri.settlement.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.YearMonth

interface SettlementRepository : JpaRepository<Settlement, Long> {

    fun findByCoupleIdAndPeriod(coupleId: Long, period: YearMonth): Settlement?
}

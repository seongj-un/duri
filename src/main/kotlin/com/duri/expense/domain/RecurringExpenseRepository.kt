package com.duri.expense.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RecurringExpenseRepository : JpaRepository<RecurringExpense, Long> {

    fun findByIdAndCoupleId(id: Long, coupleId: Long): RecurringExpense?

    fun findAllByCoupleIdOrderByDayOfMonthAscIdAsc(coupleId: Long): List<RecurringExpense>

    /**
     * 스케줄러가 훑을 대상. 커플까지 함께 가져와 건마다 재조회하지 않는다.
     * 발생일 판정은 엔티티(isDueOn)에 맡긴다.
     */
    @Query(
        """
        select r from RecurringExpense r
        join fetch r.couple
        where r.active = true
        """,
    )
    fun findAllActiveWithCouple(): List<RecurringExpense>
}

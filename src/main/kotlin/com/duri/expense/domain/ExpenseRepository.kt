package com.duri.expense.domain

import org.springframework.data.jpa.repository.JpaRepository

interface ExpenseRepository : JpaRepository<Expense, Long> {

    /** 남의 커플 지출에 손대지 못하도록 커플 id 를 조회 조건에 함께 건다. */
    fun findByIdAndCoupleId(id: Long, coupleId: Long): Expense?
}

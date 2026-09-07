package com.duri.expense.domain

import java.time.LocalDate

data class ExpenseSearchCondition(
    val coupleId: Long,
    val from: LocalDate,
    val to: LocalDate,
    val category: ExpenseCategory? = null,
    val payerId: Long? = null,
)

package com.duri.expense.domain

/** 결제자별 집계. 순잔액과 사람별 부담액이 모두 여기서 파생된다. */
data class PayerAggregate(
    val payerId: Long,
    /** 이 사람이 결제한 금액 합계. */
    val paidAmount: Long,
    /** 그중 이 사람이 부담해야 할 몫의 합계. */
    val payerShareSum: Long,
    /** 그중 상대가 갚아야 할 몫의 합계. */
    val partnerShareSum: Long,
    val count: Long,
)

data class CategoryAggregate(
    val category: ExpenseCategory,
    val amount: Long,
    val count: Long,
)

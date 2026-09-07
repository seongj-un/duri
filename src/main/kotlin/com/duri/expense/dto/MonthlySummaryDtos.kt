package com.duri.expense.dto

import com.duri.expense.domain.ExpenseCategory
import com.duri.settlement.domain.SettlementStatus
import java.time.LocalDate

data class MonthlySummaryResponse(
    /** "2026-01" */
    val period: String,
    val from: LocalDate,
    val to: LocalDate,
    val totalAmount: Long,
    val expenseCount: Long,
    val members: List<MemberSpending>,
    val categories: List<CategorySpending>,
    val balance: BalanceSummary,
    /** 아직 정산 레코드가 없으면 null. */
    val settlementStatus: SettlementStatus?,
)

data class MemberSpending(
    val member: MemberRef,
    /** 이 사람이 실제로 결제한 총액. */
    val paidAmount: Long,
    /** 부담 비율에 따라 이 사람이 져야 할 총액. */
    val burdenAmount: Long,
    /** paidAmount - burdenAmount. 양수면 받을 돈, 음수면 줄 돈. */
    val netAmount: Long,
)

data class CategorySpending(
    val category: ExpenseCategory,
    val categoryName: String,
    val amount: Long,
    val count: Long,
    /** 전체 지출 대비 비중(%). 소수 첫째 자리까지. */
    val ratio: Double,
)

/**
 * 한 달치 순잔액. [netAmount] 는 항상 0 이상이고 방향은 채권자/채무자로 표현한다.
 * 0 원이면 둘 다 null 이고, 그대로 "정산할 금액이 없음"을 뜻한다.
 */
data class BalanceSummary(
    val netAmount: Long,
    val creditor: MemberRef?,
    val debtor: MemberRef?,
)

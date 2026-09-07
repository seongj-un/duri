package com.duri.expense.dto

import com.duri.expense.domain.Expense
import com.duri.expense.domain.ExpenseCategory
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.time.Instant
import java.time.LocalDate

data class ExpenseCreateRequest(
    @field:NotNull(message = "결제자를 선택해 주세요.")
    val payerId: Long?,

    @field:NotNull(message = "금액을 입력해 주세요.")
    @field:Positive(message = "금액은 0보다 커야 합니다.")
    val amount: Long?,

    @field:NotNull(message = "카테고리를 선택해 주세요.")
    val category: ExpenseCategory?,

    /** 결제자 본인이 부담할 비율(%). 기본 반반. */
    @field:Min(value = 0, message = "부담 비율은 0 이상이어야 합니다.")
    @field:Max(value = 100, message = "부담 비율은 100 이하여야 합니다.")
    val payerBurdenRate: Int = DEFAULT_BURDEN_RATE,

    @field:NotNull(message = "지출 날짜를 입력해 주세요.")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val spentAt: LocalDate?,

    @field:Size(max = 255, message = "메모는 255자 이하로 입력해 주세요.")
    val memo: String? = null,
) {
    companion object {
        const val DEFAULT_BURDEN_RATE = 50
    }
}

/** PATCH. null 인 필드는 "변경 없음"을 뜻한다. */
data class ExpenseUpdateRequest(
    val payerId: Long? = null,

    @field:Positive(message = "금액은 0보다 커야 합니다.")
    val amount: Long? = null,

    val category: ExpenseCategory? = null,

    @field:Min(value = 0, message = "부담 비율은 0 이상이어야 합니다.")
    @field:Max(value = 100, message = "부담 비율은 100 이하여야 합니다.")
    val payerBurdenRate: Int? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val spentAt: LocalDate? = null,

    @field:Size(max = 255, message = "메모는 255자 이하로 입력해 주세요.")
    val memo: String? = null,
)

data class ExpenseResponse(
    val expenseId: Long,
    val payer: MemberRef,
    val amount: Long,
    val category: ExpenseCategory,
    val categoryName: String,
    val payerBurdenRate: Int,
    /** 결제자 본인이 부담하는 몫. */
    val payerShare: Long,
    /** 상대가 결제자에게 갚아야 할 몫. */
    val partnerShare: Long,
    val spentAt: LocalDate,
    val memo: String?,
    /** 정산이 확정되어 더는 수정할 수 없는 상태인지. */
    val locked: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun of(expense: Expense, payer: MemberRef) = ExpenseResponse(
            expenseId = expense.requiredId,
            payer = payer,
            amount = expense.amount,
            category = expense.category,
            categoryName = expense.category.displayName,
            payerBurdenRate = expense.payerBurdenRate.toInt(),
            payerShare = expense.payerShare,
            partnerShare = expense.partnerShare,
            spentAt = expense.spentAt,
            memo = expense.memo,
            locked = expense.isLocked,
            createdAt = expense.createdAt,
        )
    }
}

data class MemberRef(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String? = null,
)

data class ExpensePageResponse(
    val content: List<ExpenseResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    /** 페이지가 아니라 필터 전체의 합계. 목록 상단에 그대로 띄우기 위한 값. */
    val totalAmount: Long,
)

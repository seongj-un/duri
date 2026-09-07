package com.duri.expense.dto

import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.domain.RecurringExpense
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class RecurringExpenseCreateRequest(
    @field:NotNull(message = "결제자를 선택해 주세요.")
    val payerId: Long?,

    @field:NotBlank(message = "이름을 입력해 주세요.")
    @field:Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    val title: String?,

    @field:NotNull(message = "금액을 입력해 주세요.")
    @field:Positive(message = "금액은 0보다 커야 합니다.")
    val amount: Long?,

    @field:NotNull(message = "카테고리를 선택해 주세요.")
    val category: ExpenseCategory?,

    /** 생략하면 카테고리 프리셋을 따른다. 등록 시점에 확정되어 저장된다. */
    @field:Min(value = 0, message = "부담 비율은 0 이상이어야 합니다.")
    @field:Max(value = 100, message = "부담 비율은 100 이하여야 합니다.")
    val payerBurdenRate: Int? = null,

    @field:NotNull(message = "발생일을 입력해 주세요.")
    @field:Min(value = 1, message = "발생일은 1일 이상이어야 합니다.")
    @field:Max(value = 28, message = "발생일은 28일 이하여야 합니다.")
    val dayOfMonth: Int?,

    /** 생략하면 오늘부터. 이 날짜 이후의 발생일부터 지출이 만들어진다. */
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val startsOn: LocalDate? = null,

    /** 구독 해지일처럼 반복이 끝나는 날. 생략하면 무기한. */
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val endsOn: LocalDate? = null,

    @field:Size(max = 255, message = "메모는 255자 이하로 입력해 주세요.")
    val memo: String? = null,
)

/** PATCH. null 인 필드는 "변경 없음"을 뜻한다. */
data class RecurringExpenseUpdateRequest(
    val payerId: Long? = null,

    @field:Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    val title: String? = null,

    @field:Positive(message = "금액은 0보다 커야 합니다.")
    val amount: Long? = null,

    val category: ExpenseCategory? = null,

    @field:Min(value = 0, message = "부담 비율은 0 이상이어야 합니다.")
    @field:Max(value = 100, message = "부담 비율은 100 이하여야 합니다.")
    val payerBurdenRate: Int? = null,

    @field:Min(value = 1, message = "발생일은 1일 이상이어야 합니다.")
    @field:Max(value = 28, message = "발생일은 28일 이하여야 합니다.")
    val dayOfMonth: Int? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val startsOn: LocalDate? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val endsOn: LocalDate? = null,

    @field:Size(max = 255, message = "메모는 255자 이하로 입력해 주세요.")
    val memo: String? = null,
)

data class RecurringExpenseResponse(
    val recurringExpenseId: Long,
    val payer: MemberRef,
    val title: String,
    val amount: Long,
    val category: ExpenseCategory,
    val categoryName: String,
    val payerBurdenRate: Int,
    val dayOfMonth: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val memo: String?,
    val active: Boolean,
    /** 다음으로 지출이 만들어질 날. 더 만들 것이 없으면 null. */
    val nextDueDate: LocalDate?,
) {
    companion object {
        fun of(recurring: RecurringExpense, payer: MemberRef, today: LocalDate) = RecurringExpenseResponse(
            recurringExpenseId = recurring.requiredId,
            payer = payer,
            title = recurring.title,
            amount = recurring.amount,
            category = recurring.category,
            categoryName = recurring.category.displayName,
            payerBurdenRate = recurring.payerBurdenRate.toInt(),
            dayOfMonth = recurring.dayOfMonth.toInt(),
            startsOn = recurring.startsOn,
            endsOn = recurring.endsOn,
            memo = recurring.memo,
            active = recurring.active,
            nextDueDate = recurring.nextDueDateFrom(today),
        )
    }
}

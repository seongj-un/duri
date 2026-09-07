package com.duri.expense.dto

import com.duri.expense.domain.ExpenseCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class BurdenPresetResponse(
    val category: ExpenseCategory,
    val categoryName: String,
    val rates: List<MemberBurdenRate>,
    /** false 면 저장된 프리셋 없이 기본값(반반)을 보여주는 중이다. */
    val customized: Boolean,
)

data class MemberBurdenRate(
    val member: MemberRef,
    val burdenRate: Int,
)

data class BurdenPresetUpdateRequest(
    @field:NotEmpty(message = "변경할 프리셋을 하나 이상 보내주세요.")
    @field:Valid
    val presets: List<Item>,
) {
    /**
     * 한 사람의 비율만 보내면 나머지는 자동으로 채워진다.
     * "월세는 성준이 30%" 라고만 말하면 지현은 70% 다.
     */
    data class Item(
        @field:NotNull(message = "카테고리를 선택해 주세요.")
        val category: ExpenseCategory?,

        @field:NotNull(message = "기준이 될 사람을 지정해 주세요.")
        val userId: Long?,

        @field:NotNull(message = "부담 비율을 입력해 주세요.")
        @field:Min(value = 0, message = "부담 비율은 0 이상이어야 합니다.")
        @field:Max(value = 100, message = "부담 비율은 100 이하여야 합니다.")
        val burdenRate: Int?,
    )
}

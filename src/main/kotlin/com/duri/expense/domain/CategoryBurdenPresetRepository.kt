package com.duri.expense.domain

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryBurdenPresetRepository : JpaRepository<CategoryBurdenPreset, Long> {

    fun findAllByCoupleId(coupleId: Long): List<CategoryBurdenPreset>

    fun findByCoupleIdAndCategory(coupleId: Long, category: ExpenseCategory): CategoryBurdenPreset?
}

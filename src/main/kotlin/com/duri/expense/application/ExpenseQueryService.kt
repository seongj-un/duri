package com.duri.expense.application

import com.duri.couple.application.CoupleContextLoader
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.domain.ExpenseQueryRepository
import com.duri.expense.domain.ExpenseSearchCondition
import com.duri.expense.dto.ExpensePageResponse
import com.duri.expense.dto.ExpenseResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Service
class ExpenseQueryService(
    private val queryRepository: ExpenseQueryRepository,
    private val coupleContextLoader: CoupleContextLoader,
) {

    @Transactional(readOnly = true)
    fun list(
        userId: Long,
        period: YearMonth,
        category: ExpenseCategory?,
        payerId: Long?,
        pageable: Pageable,
    ): ExpensePageResponse {
        val context = coupleContextLoader.loadActive(userId)
        val condition = ExpenseSearchCondition(
            coupleId = context.coupleId,
            from = period.atDay(1),
            to = period.atEndOfMonth(),
            category = category,
            payerId = payerId,
        )

        val page = queryRepository.findPage(condition, pageable)

        return ExpensePageResponse(
            content = page.content.map { ExpenseResponse.of(it, context.memberRefOf(it.payerId)) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
            totalAmount = queryRepository.sumAmount(condition),
        )
    }
}

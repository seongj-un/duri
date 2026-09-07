package com.duri.expense.application

import com.duri.couple.application.CoupleContextLoader
import com.duri.expense.domain.ExpenseQueryRepository
import com.duri.expense.domain.ExpenseSearchCondition
import com.duri.expense.dto.BalanceSummary
import com.duri.expense.dto.CategorySpending
import com.duri.expense.dto.MemberSpending
import com.duri.expense.dto.MonthlySummaryResponse
import com.duri.settlement.domain.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 월별 뷰 한 화면에 필요한 값을 한 번에 만든다.
 *
 * 순잔액은 지출에서 파생되는 값이라 정산 레코드가 없어도 계산된다.
 * 정산 확정(W5-6)은 이 값을 특정 시점에 고정하는 일이 된다.
 *
 * 기간은 달력 월 그대로다. couples.settlement_day 는 "이 날 정산하자"고 알려주는
 * 리마인드 시점이지 기간의 경계가 아니다.
 */
@Service
class MonthlySummaryService(
    private val queryRepository: ExpenseQueryRepository,
    private val settlementRepository: SettlementRepository,
    private val coupleContextLoader: CoupleContextLoader,
    private val balanceCalculator: BalanceCalculator,
) {

    @Transactional(readOnly = true)
    fun monthly(userId: Long, period: YearMonth): MonthlySummaryResponse {
        val context = coupleContextLoader.loadActive(userId)
        val condition = ExpenseSearchCondition(
            coupleId = context.coupleId,
            from = period.atDay(1),
            to = period.atEndOfMonth(),
        )

        val byPayer = queryRepository.aggregateByPayer(condition).associateBy { it.payerId }
        val totalAmount = byPayer.values.sumOf { it.paidAmount }
        val expenseCount = byPayer.values.sumOf { it.count }

        val members = context.members.map { member ->
            val userIdOfMember = member.user.requiredId
            val partnerId = context.partnerOf(userIdOfMember).user.requiredId
            val mine = byPayer[userIdOfMember]
            val partners = byPayer[partnerId]

            val paid = mine?.paidAmount ?: 0L
            // 내가 낸 것 중 내 몫 + 상대가 낸 것 중 내 몫
            val burden = (mine?.payerShareSum ?: 0L) + (partners?.partnerShareSum ?: 0L)

            MemberSpending(
                member = context.memberRefOf(userIdOfMember),
                paidAmount = paid,
                burdenAmount = burden,
                netAmount = paid - burden,
            )
        }

        return MonthlySummaryResponse(
            period = period.format(PERIOD_FORMAT),
            from = condition.from,
            to = condition.to,
            totalAmount = totalAmount,
            expenseCount = expenseCount,
            members = members,
            categories = queryRepository.aggregateByCategory(condition).map {
                CategorySpending(
                    category = it.category,
                    categoryName = it.category.displayName,
                    amount = it.amount,
                    count = it.count,
                    ratio = it.amount.percentageOf(totalAmount),
                )
            },
            balance = balanceCalculator.calculate(context, period).toSummary(context),
            settlementStatus = settlementRepository
                .findByCoupleIdAndPeriod(context.coupleId, period)
                ?.status,
        )
    }

    private fun Long.percentageOf(total: Long): Double =
        if (total <= 0) 0.0
        else BigDecimal(this * 100).divide(BigDecimal(total), 1, RoundingMode.HALF_UP).toDouble()

    private companion object {
        val PERIOD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM")
    }
}

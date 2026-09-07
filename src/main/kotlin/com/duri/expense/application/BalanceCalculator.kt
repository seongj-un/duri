package com.duri.expense.application

import com.duri.couple.application.CoupleContext
import com.duri.expense.domain.ExpenseQueryRepository
import com.duri.expense.domain.ExpenseSearchCondition
import org.springframework.stereotype.Component
import java.time.YearMonth
import kotlin.math.abs

/**
 * 한 달치 순잔액을 지출에서 계산한다.
 *
 * 월별 뷰(실시간 표시)와 정산 확정(그 값을 고정)이 같은 계산을 써야 하므로 여기 한 곳에 둔다.
 */
@Component
class BalanceCalculator(
    private val queryRepository: ExpenseQueryRepository,
) {

    fun calculate(context: CoupleContext, period: YearMonth): CoupleBalance {
        val reference = context.members.first().user.requiredId
        val partner = context.partnerOf(reference).user.requiredId

        val byPayer = queryRepository
            .aggregateByPayer(
                ExpenseSearchCondition(
                    coupleId = context.coupleId,
                    from = period.atDay(1),
                    to = period.atEndOfMonth(),
                ),
            )
            .associateBy { it.payerId }

        // 내가 낸 것 중 상대 몫 - 상대가 낸 것 중 내 몫
        val signed = (byPayer[reference]?.partnerShareSum ?: 0L) -
            (byPayer[partner]?.partnerShareSum ?: 0L)

        return CoupleBalance(referenceUserId = reference, partnerUserId = partner, signedAmount = signed)
    }
}

/**
 * [signedAmount] 는 [referenceUserId] 기준이다. 양수면 그 사람이 받을 돈.
 * 밖으로 나갈 때는 부호 대신 채권자/채무자로 방향을 표현한다.
 */
data class CoupleBalance(
    val referenceUserId: Long,
    val partnerUserId: Long,
    val signedAmount: Long,
) {
    val netAmount: Long get() = abs(signedAmount)

    val creditorId: Long? get() = when {
        signedAmount > 0 -> referenceUserId
        signedAmount < 0 -> partnerUserId
        else -> null
    }

    val debtorId: Long? get() = when {
        signedAmount > 0 -> partnerUserId
        signedAmount < 0 -> referenceUserId
        else -> null
    }
}

/** 응답용 표현으로 옮긴다. 금액은 절댓값, 방향은 사람으로. */
internal fun CoupleBalance.toSummary(context: CoupleContext) = com.duri.expense.dto.BalanceSummary(
    netAmount = netAmount,
    creditor = creditorId?.let(context::memberRefOf),
    debtor = debtorId?.let(context::memberRefOf),
)

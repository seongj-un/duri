package com.duri.settlement.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.application.CoupleContext
import com.duri.couple.application.CoupleContextLoader
import com.duri.couple.domain.CoupleRepository
import com.duri.expense.application.BalanceCalculator
import com.duri.expense.application.memberRefOf
import com.duri.expense.domain.ExpenseQueryRepository
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.domain.ExpenseSearchCondition
import com.duri.settlement.domain.Settlement
import com.duri.settlement.domain.SettlementRepository
import com.duri.settlement.domain.SettlementStatus
import com.duri.settlement.dto.SettlementHistoryResponse
import com.duri.settlement.dto.SettlementResponse
import com.duri.settlement.dto.TransferGuide
import com.duri.user.application.bank
import com.duri.user.domain.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 월 정산 사이클.
 *
 *  마감 : 달이 지나면 그 달의 순잔액은 더 움직이지 않는다(지출을 더 넣지 않는 한).
 *  확정 : 그 시점의 순잔액을 정산 레코드에 고정한다.
 *  잠금 : 그 달의 지출에 settlement_id 를 채워 수정·삭제를 막는다.
 *
 * 확정은 멱등하다. 두 사람이 동시에 눌러도, 같은 사람이 두 번 눌러도
 * 결과는 하나의 정산이고 금액도 바뀌지 않는다.
 */
@Service
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseQueryRepository: ExpenseQueryRepository,
    private val accountRepository: AccountRepository,
    private val coupleRepository: CoupleRepository,
    private val coupleContextLoader: CoupleContextLoader,
    private val balanceCalculator: BalanceCalculator,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 특정 달의 정산 현황. 확정 전이면 지출에서 실시간으로 계산해 보여준다. */
    @Transactional(readOnly = true)
    fun get(userId: Long, period: YearMonth): SettlementResponse {
        val context = coupleContextLoader.loadActive(userId)
        val settlement = settlementRepository.findByCoupleIdAndPeriod(context.coupleId, period)
        return buildResponse(context, period, settlement)
    }

    @Transactional(readOnly = true)
    fun history(userId: Long): List<SettlementHistoryResponse> {
        val context = coupleContextLoader.loadActive(userId)
        return settlementRepository.findAllByCoupleIdOrderByPeriodDesc(context.coupleId)
            .map {
                SettlementHistoryResponse(
                    settlementId = it.requiredId,
                    period = it.period.format(PERIOD_FORMAT),
                    status = it.status,
                    netAmount = it.netAmount,
                    creditor = it.creditorId?.let(context::memberRefOf),
                    debtor = it.debtorId?.let(context::memberRefOf),
                    confirmedAt = it.confirmedAt,
                )
            }
    }

    /**
     * 정산을 확정한다.
     *
     * 이미 확정된 달이면 아무것도 바꾸지 않고 그대로 돌려준다(멱등).
     * 커플 행에 비관적 락을 잡아 같은 커플의 동시 확정을 직렬화하고,
     * 그래도 뚫리면 UNIQUE(couple_id, period) 가 막는다.
     */
    @Transactional
    fun confirm(userId: Long, period: YearMonth): SettlementResponse {
        val context = coupleContextLoader.loadActive(userId)
        requireSettleable(period)

        coupleRepository.findByIdForUpdate(context.coupleId)
            ?: throw BusinessException(ErrorCode.COUPLE_NOT_FOUND)

        val existing = settlementRepository.findByCoupleIdAndPeriod(context.coupleId, period)
        if (existing != null && existing.isConfirmed) {
            log.info(
                "settlement already confirmed: coupleId={} period={} settlementId={}",
                context.coupleId, period, existing.requiredId,
            )
            return buildResponse(context, period, existing)
        }

        val balance = balanceCalculator.calculate(context, period)
        val settlement = existing ?: settlementRepository.save(Settlement(context.couple, period))

        settlement.updateBalance(balance.referenceUserId, balance.partnerUserId, balance.signedAmount)
        settlement.confirm(userId, clock.instant())
        settlementRepository.flush()

        val locked = expenseRepository.lockToSettlement(
            coupleId = context.coupleId,
            from = period.atDay(1),
            to = period.atEndOfMonth(),
            settlementId = settlement.requiredId,
        )
        log.info(
            "settlement confirmed: coupleId={} period={} netAmount={} lockedExpenses={}",
            context.coupleId, period, settlement.netAmount, locked,
        )

        return buildResponse(context, period, settlement)
    }

    /** 아직 시작도 하지 않은 달을 미리 확정해 버리면 그 달의 지출을 못 넣게 된다. */
    private fun requireSettleable(period: YearMonth) {
        if (period.isAfter(YearMonth.now(clock))) {
            throw BusinessException(ErrorCode.FUTURE_PERIOD_NOT_SETTLEABLE)
        }
    }

    private fun buildResponse(
        context: CoupleContext,
        period: YearMonth,
        settlement: Settlement?,
    ): SettlementResponse {
        val confirmed = settlement?.takeIf { it.isConfirmed }

        // 확정본은 저장된 금액을 쓰고, 확정 전이면 지출에서 다시 계산한다.
        val netAmount: Long
        val creditorId: Long?
        val debtorId: Long?
        if (confirmed != null) {
            netAmount = confirmed.netAmount
            creditorId = confirmed.creditorId
            debtorId = confirmed.debtorId
        } else {
            val balance = balanceCalculator.calculate(context, period)
            netAmount = balance.netAmount
            creditorId = balance.creditorId
            debtorId = balance.debtorId
        }

        val condition = ExpenseSearchCondition(
            coupleId = context.coupleId,
            from = period.atDay(1),
            to = period.atEndOfMonth(),
        )
        val perPayer = expenseQueryRepository.aggregateByPayer(condition)

        return SettlementResponse(
            settlementId = settlement?.id,
            period = period.format(PERIOD_FORMAT),
            from = condition.from,
            to = condition.to,
            status = confirmed?.status ?: SettlementStatus.OPEN,
            netAmount = netAmount,
            creditor = creditorId?.let(context::memberRefOf),
            debtor = debtorId?.let(context::memberRefOf),
            expenseCount = perPayer.sumOf { it.count },
            totalAmount = perPayer.sumOf { it.paidAmount },
            transfer = creditorId?.let { transferGuideFor(it, netAmount) },
            confirmedAt = confirmed?.confirmedAt,
            confirmedBy = confirmed?.confirmedBy?.let(context::memberRefOf),
        )
    }

    /** 채권자가 계좌를 등록해 두지 않았으면 안내를 만들 수 없다. 그건 오류가 아니라 null 이다. */
    private fun transferGuideFor(creditorId: Long, amount: Long): TransferGuide? {
        if (amount <= 0) return null
        val account = accountRepository.findByUserId(creditorId) ?: return null
        val bank = account.bank()

        return TransferGuide(
            bank = bank,
            bankName = account.bankName,
            accountNo = account.accountNo,
            holderName = account.holderName,
            amount = amount,
            copyText = "${account.bankName} ${account.accountNo} ${account.holderName} ${"%,d".format(amount)}원",
        )
    }

    private companion object {
        val PERIOD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM")
    }
}

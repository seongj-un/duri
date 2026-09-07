package com.duri.notification.application

import com.duri.couple.application.CoupleContextLoader
import com.duri.expense.application.BalanceCalculator
import com.duri.notification.domain.Notification
import com.duri.notification.domain.NotificationRepository
import com.duri.notification.domain.NotificationType
import com.duri.realtime.application.CoupleEventPublisher
import com.duri.realtime.domain.CoupleEventType
import com.duri.settlement.domain.SettlementRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 커플 하나에 대한 정산일 리마인드.
 *
 * 커플마다 독립된 트랜잭션이다. 한 커플의 알림 생성이 실패해도
 * 다른 커플의 알림까지 함께 롤백되면 안 되기 때문이다.
 */
@Component
class SettlementReminder(
    private val notificationRepository: NotificationRepository,
    private val settlementRepository: SettlementRepository,
    private val coupleContextLoader: CoupleContextLoader,
    private val balanceCalculator: BalanceCalculator,
    private val eventPublisher: CoupleEventPublisher,
    private val clock: Clock,
) {

    /**
     * 기준일에 알리는 대상은 **지난달**이다.
     * `POST /settlements/confirm` 이 기간을 생략했을 때 지난달을 마감하는 것과 같은 규칙이다.
     */
    @Transactional
    fun remind(coupleId: Long, period: YearMonth): ReminderOutcome {
        val context = coupleContextLoader.loadByCoupleId(coupleId)

        if (settlementRepository.findByCoupleIdAndPeriod(coupleId, period)?.isConfirmed == true) {
            return ReminderOutcome.ALREADY_SETTLED
        }

        val periodKey = period.format(PERIOD_KEY)
        val targets = context.members
            .map { it.user.requiredId }
            .filterNot {
                notificationRepository.existsByUserIdAndTypeAndPeriod(
                    it, NotificationType.SETTLEMENT_REMINDER, periodKey,
                )
            }
        if (targets.isEmpty()) return ReminderOutcome.ALREADY_NOTIFIED

        val balance = balanceCalculator.calculate(context, period)
        val title = "${period.monthValue}월 정산할 시간이에요"
        val body = when (val creditorId = balance.creditorId) {
            null -> "이번 달은 주고받을 금액이 없어요."
            else -> {
                val creditor = context.memberOf(creditorId).user.nickname
                val debtor = context.memberOf(balance.debtorId!!).user.nickname
                "${debtor}님이 ${creditor}님에게 ${"%,d".format(balance.netAmount)}원 보내면 정산 완료예요."
            }
        }

        val now = clock.instant()
        targets.forEach { userId ->
            notificationRepository.save(
                Notification.settlementReminder(userId, coupleId, period, title, body, now),
            )
        }

        eventPublisher.publish(
            type = CoupleEventType.NOTIFICATION_CREATED,
            coupleId = coupleId,
            period = period,
        )
        return ReminderOutcome.SENT
    }

    private companion object {
        val PERIOD_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMM")
    }
}

enum class ReminderOutcome {
    SENT,
    ALREADY_SETTLED,
    ALREADY_NOTIFIED,
    FAILED,
}

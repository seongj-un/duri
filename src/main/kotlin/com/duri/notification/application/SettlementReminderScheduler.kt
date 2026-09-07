package com.duri.notification.application

import com.duri.couple.domain.CoupleRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * 매일 한 번 돌면서 오늘이 정산 기준일인 커플에게 리마인드를 보낸다.
 *
 * 같은 사람에게 같은 기간의 알림을 두 번 보내지 않는 것은
 * uq_notifications_dedupe 유니크 인덱스가 보장한다.
 */
@Component
class SettlementReminderScheduler(
    private val coupleRepository: CoupleRepository,
    private val reminder: SettlementReminder,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${duri.notification.reminder-cron}", zone = "Asia/Seoul")
    fun runDaily() {
        val report = remindOn(LocalDate.now(clock))
        log.info("settlement reminder finished: {}", report)
    }

    /** 날짜를 받는 형태로 열어 두어 테스트와 실제 실행이 같은 경로를 탄다. */
    fun remindOn(today: LocalDate): ReminderReport {
        val couples = coupleRepository.findActiveBySettlementDay(today.dayOfMonth.toShort())
        val period = YearMonth.from(today).minusMonths(1)

        val outcomes = couples.map { couple ->
            runCatching { reminder.remind(couple.requiredId, period) }
                .getOrElse { error ->
                    log.error("settlement reminder failed: coupleId={}", couple.requiredId, error)
                    ReminderOutcome.FAILED
                }
        }

        return ReminderReport(
            date = today,
            period = period.toString(),
            scanned = couples.size,
            sent = outcomes.count { it == ReminderOutcome.SENT },
            skipped = outcomes.count { it == ReminderOutcome.ALREADY_SETTLED || it == ReminderOutcome.ALREADY_NOTIFIED },
            failed = outcomes.count { it == ReminderOutcome.FAILED },
        )
    }
}

data class ReminderReport(
    val date: LocalDate,
    val period: String,
    val scanned: Int,
    val sent: Int,
    val skipped: Int,
    val failed: Int,
)

package com.duri.expense.application

import com.duri.expense.domain.RecurringExpenseRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * 매일 한 번 돌면서 그날 발생할 반복지출을 실제 지출로 만든다.
 *
 * 발생일이 지났는데 아직 안 만들어진 건도 함께 따라잡는다.
 * 서버가 발생일에 꺼져 있었더라도 다음 실행에서 메워지도록 하기 위해서다.
 *
 * 인스턴스가 여러 대여서 같은 시각에 동시에 돌더라도
 * uq_expenses_recurring_period 유니크 인덱스가 중복 생성을 막는다.
 */
@Component
class RecurringExpenseScheduler(
    private val recurringExpenseRepository: RecurringExpenseRepository,
    private val generator: RecurringExpenseGenerator,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${duri.recurring.cron}", zone = "Asia/Seoul")
    fun runDaily() {
        val report = generateFor(LocalDate.now(clock))
        log.info("recurring expense generation finished: {}", report)
    }

    /** 날짜를 받는 형태로 열어 두어 테스트와 수동 실행이 같은 경로를 타게 한다. */
    fun generateFor(today: LocalDate): GenerationReport {
        val targets = recurringExpenseRepository.findAllActiveWithCouple()
            .filter { it.isDueOn(today) }

        val outcomes = targets.map { recurring ->
            runCatching { generator.generate(recurring.requiredId, today) }
                .getOrElse { error ->
                    // 한 건이 깨져도 나머지는 계속 만든다
                    log.error(
                        "recurring expense generation failed: recurringExpenseId={} today={}",
                        recurring.requiredId, today, error,
                    )
                    GenerationOutcome.FAILED
                }
        }

        return GenerationReport(
            date = today,
            scanned = targets.size,
            created = outcomes.count { it == GenerationOutcome.CREATED },
            skipped = outcomes.count { it != GenerationOutcome.CREATED && it != GenerationOutcome.FAILED },
            failed = outcomes.count { it == GenerationOutcome.FAILED },
        )
    }
}

data class GenerationReport(
    val date: LocalDate,
    val scanned: Int,
    val created: Int,
    val skipped: Int,
    val failed: Int,
)

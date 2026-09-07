package com.duri.settlement

import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.settlement.application.SettlementService
import com.duri.settlement.domain.SettlementRepository
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("정산 확정 동시성")
class SettlementConcurrencyTest(
    private val settlementService: SettlementService,
    private val expenseService: ExpenseService,
    private val settlementRepository: SettlementRepository,
    private val expenseRepository: ExpenseRepository,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    /**
     * 두 사람이 같은 순간에 확정 버튼을 눌러도 정산은 하나여야 한다.
     *
     * 커플 행 비관적 락이 두 트랜잭션을 줄 세우고,
     * 뒤에 들어온 쪽은 이미 확정된 정산을 그대로 돌려받는다.
     * 락이 없더라도 UNIQUE(couple_id, period) 가 최후로 막는다.
     */
    @Test
    fun `두 사람이 동시에 확정해도 정산은 하나만 생긴다`() {
        val couple = coupleFixture.active()
        val january = YearMonth.of(2026, 1)
        expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(
                couple.ownerId, 30_000, ExpenseCategory.GROCERY, 50, LocalDate.of(2026, 1, 15), null,
            ),
        )

        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        val results = mutableListOf<Result<Long?>>()

        listOf(couple.ownerId, couple.partnerId).forEach { userId ->
            executor.submit {
                try {
                    start.await()
                    val outcome = runCatching { settlementService.confirm(userId, january).settlementId }
                    synchronized(results) { results += outcome }
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()

        // 두 요청 모두 성공하고, 같은 정산을 가리켜야 한다
        assertThat(results).hasSize(2)
        assertThat(results).allMatch { it.isSuccess }
        assertThat(results.mapNotNull { it.getOrNull() }.distinct()).hasSize(1)

        assertThat(settlementRepository.findAll()).hasSize(1)
        assertThat(expenseRepository.findAll()).allMatch { it.isLocked }
    }
}

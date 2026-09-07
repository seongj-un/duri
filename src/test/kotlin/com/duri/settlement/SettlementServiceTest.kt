package com.duri.settlement

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.expense.dto.ExpenseUpdateRequest
import com.duri.settlement.application.SettlementService
import com.duri.settlement.domain.SettlementRepository
import com.duri.settlement.domain.SettlementStatus
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import com.duri.user.application.AccountService
import com.duri.user.domain.Bank
import com.duri.user.dto.AccountRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("정산 사이클: 마감 · 확정 · 잠금")
class SettlementServiceTest(
    private val settlementService: SettlementService,
    private val expenseService: ExpenseService,
    private val accountService: AccountService,
    private val settlementRepository: SettlementRepository,
    private val expenseRepository: ExpenseRepository,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple

    /** 테스트 시계는 2026-01-01 에 고정되어 있다. */
    private val january = YearMonth.of(2026, 1)

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active(ownerNickname = "성준", partnerNickname = "지현")
    }

    private fun spend(payerId: Long, amount: Long, rate: Int = 50, day: Int = 15, month: Int = 1) =
        expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(
                payerId, amount, ExpenseCategory.GROCERY, rate, LocalDate.of(2026, month, day), null,
            ),
        )

    @Test
    fun `확정 전에는 OPEN 이고 금액이 실시간으로 따라온다`() {
        spend(couple.ownerId, 30_000)

        val before = settlementService.get(couple.ownerId, january)
        assertThat(before.status).isEqualTo(SettlementStatus.OPEN)
        assertThat(before.settlementId).isNull()
        assertThat(before.netAmount).isEqualTo(15_000)

        spend(couple.ownerId, 10_000)

        val after = settlementService.get(couple.ownerId, january)
        assertThat(after.netAmount).isEqualTo(20_000)
    }

    @Test
    fun `확정하면 금액이 고정되고 그 달 지출이 잠긴다`() {
        spend(couple.ownerId, 30_000)
        spend(couple.partnerId, 10_000)

        val confirmed = settlementService.confirm(couple.ownerId, january)

        assertThat(confirmed.status).isEqualTo(SettlementStatus.CONFIRMED)
        assertThat(confirmed.settlementId).isNotNull()
        assertThat(confirmed.netAmount).isEqualTo(10_000)
        assertThat(confirmed.creditor?.nickname).isEqualTo("성준")
        assertThat(confirmed.debtor?.nickname).isEqualTo("지현")
        assertThat(confirmed.confirmedBy?.nickname).isEqualTo("성준")
        assertThat(confirmed.confirmedAt).isNotNull()

        assertThat(expenseRepository.findAll()).allMatch { it.isLocked }
    }

    @Test
    fun `확정된 달의 지출은 수정도 삭제도 할 수 없다`() {
        val expense = spend(couple.ownerId, 30_000)
        settlementService.confirm(couple.ownerId, january)

        assertThatThrownBy {
            expenseService.update(couple.ownerId, expense.expenseId, ExpenseUpdateRequest(amount = 1_000))
        }.isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPENSE_LOCKED)

        assertThatThrownBy { expenseService.delete(couple.ownerId, expense.expenseId) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPENSE_LOCKED)
    }

    @Test
    fun `두 번 확정해도 정산은 하나이고 금액도 그대로다`() {
        spend(couple.ownerId, 30_000)

        val first = settlementService.confirm(couple.ownerId, january)
        val second = settlementService.confirm(couple.ownerId, january)

        assertThat(second.settlementId).isEqualTo(first.settlementId)
        assertThat(second.netAmount).isEqualTo(first.netAmount)
        assertThat(second.confirmedAt).isEqualTo(first.confirmedAt)
        assertThat(settlementRepository.findAll()).hasSize(1)
    }

    @Test
    fun `상대가 눌러도 이미 확정된 정산이 그대로 돌아온다`() {
        spend(couple.ownerId, 30_000)
        val byOwner = settlementService.confirm(couple.ownerId, january)

        val byPartner = settlementService.confirm(couple.partnerId, january)

        assertThat(byPartner.settlementId).isEqualTo(byOwner.settlementId)
        // 확정자는 처음 누른 사람으로 남는다
        assertThat(byPartner.confirmedBy?.nickname).isEqualTo("성준")
        assertThat(settlementRepository.findAll()).hasSize(1)
    }

    @Test
    fun `확정 후에는 그 달에 지출을 새로 넣을 수 없다`() {
        spend(couple.ownerId, 30_000)
        settlementService.confirm(couple.ownerId, january)

        assertThatThrownBy { spend(couple.ownerId, 50_000) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERIOD_ALREADY_SETTLED)

        // 확정 금액은 그대로여야 한다
        assertThat(settlementService.get(couple.ownerId, january).netAmount).isEqualTo(15_000)
    }

    @Test
    fun `지출이 없어도 0원으로 확정할 수 있다`() {
        val confirmed = settlementService.confirm(couple.ownerId, january)

        assertThat(confirmed.status).isEqualTo(SettlementStatus.CONFIRMED)
        assertThat(confirmed.netAmount).isZero()
        assertThat(confirmed.creditor).isNull()
        assertThat(confirmed.debtor).isNull()
        assertThat(confirmed.transfer).isNull()
    }

    @Test
    fun `아직 오지 않은 달은 확정할 수 없다`() {
        assertThatThrownBy { settlementService.confirm(couple.ownerId, YearMonth.of(2026, 2)) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FUTURE_PERIOD_NOT_SETTLEABLE)
    }

    @Test
    fun `다른 달의 지출은 잠기지 않는다`() {
        val januaryExpense = spend(couple.ownerId, 30_000, month = 1)

        settlementService.confirm(couple.ownerId, january)

        val locked = expenseRepository.findById(januaryExpense.expenseId).orElseThrow()
        assertThat(locked.isLocked).isTrue()
        // 2월 지출은 아직 확정되지 않았으므로 그대로 기록할 수 있어야 한다
        val februaryExpense = spend(couple.ownerId, 20_000, month = 2)
        assertThat(expenseRepository.findById(februaryExpense.expenseId).orElseThrow().isLocked).isFalse()
    }

    @Test
    fun `확정 이력이 최신순으로 쌓인다`() {
        spend(couple.ownerId, 30_000, month = 1)
        settlementService.confirm(couple.ownerId, january)
        mutableClock.advance(java.time.Duration.ofDays(40))
        spend(couple.ownerId, 50_000, month = 2)
        settlementService.confirm(couple.ownerId, YearMonth.of(2026, 2))

        val history = settlementService.history(couple.ownerId)

        assertThat(history.map { it.period }).containsExactly("2026-02", "2026-01")
        assertThat(history.map { it.netAmount }).containsExactly(25_000, 15_000)
        assertThat(history).allMatch { it.status == SettlementStatus.CONFIRMED }
    }

    @Test
    fun `채권자가 계좌를 등록해 두면 송금 안내가 함께 온다`() {
        accountService.upsert(
            couple.ownerId,
            AccountRequest(Bank.KAKAOBANK, "3333-01-1234567", "박성준"),
        )
        spend(couple.ownerId, 1_420_000)

        val settlement = settlementService.confirm(couple.ownerId, january)

        val transfer = settlement.transfer!!
        assertThat(transfer.bank).isEqualTo(Bank.KAKAOBANK)
        assertThat(transfer.bankName).isEqualTo("카카오뱅크")
        assertThat(transfer.accountNo).isEqualTo("3333011234567")
        assertThat(transfer.holderName).isEqualTo("박성준")
        assertThat(transfer.amount).isEqualTo(710_000)
        assertThat(transfer.copyText).isEqualTo("카카오뱅크 3333011234567 박성준 710,000원")
    }

    @Test
    fun `채권자가 계좌를 등록하지 않았으면 송금 안내는 비어 있다`() {
        // 계좌를 등록한 쪽은 채무자다
        accountService.upsert(couple.partnerId, AccountRequest(Bank.KB, "12345678901234", "김지현"))
        spend(couple.ownerId, 30_000)

        val settlement = settlementService.confirm(couple.ownerId, january)

        assertThat(settlement.creditor?.nickname).isEqualTo("성준")
        assertThat(settlement.transfer).isNull()
    }

    @Test
    fun `커플이 없으면 정산을 볼 수 없다`() {
        val (soloUserId, _) = coupleFixture.pending("혼자")

        assertThatThrownBy { settlementService.get(soloUserId, january) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPLE_NOT_ACTIVE)
    }
}

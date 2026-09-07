package com.duri.expense

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.domain.ExpenseRepository
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.expense.dto.ExpenseUpdateRequest
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.Fixtures
import com.duri.couple.domain.CoupleRepository
import com.duri.settlement.domain.Settlement
import com.duri.settlement.domain.SettlementRepository
import com.duri.support.IntegrationTestBase
import com.duri.user.domain.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("지출 등록 · 수정 · 삭제")
class ExpenseServiceTest(
    private val expenseService: ExpenseService,
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val coupleFixture: CoupleFixture,
    private val settlementRepository: SettlementRepository,
    private val coupleRepository: CoupleRepository,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active()
    }

    private fun request(
        payerId: Long = couple.ownerId,
        amount: Long = 30_000,
        rate: Int = 50,
        category: ExpenseCategory = ExpenseCategory.GROCERY,
        spentAt: LocalDate = LocalDate.of(2026, 1, 15),
        memo: String? = "장보기",
    ) = ExpenseCreateRequest(payerId, amount, category, rate, spentAt, memo)

    @Test
    fun `지출을 등록하면 부담 몫이 함께 계산되어 돌아온다`() {
        val created = expenseService.create(couple.ownerId, request(amount = 30_000, rate = 50))

        assertThat(created.amount).isEqualTo(30_000)
        assertThat(created.payerShare).isEqualTo(15_000)
        assertThat(created.partnerShare).isEqualTo(15_000)
        assertThat(created.payer.nickname).isEqualTo("성준")
        assertThat(created.categoryName).isEqualTo("장보기")
        assertThat(created.locked).isFalse()
    }

    @Test
    fun `파트너가 결제한 지출도 내가 대신 등록할 수 있다`() {
        val created = expenseService.create(couple.ownerId, request(payerId = couple.partnerId))

        assertThat(created.payer.userId).isEqualTo(couple.partnerId)
        assertThat(created.payer.nickname).isEqualTo("지현")
    }

    @Test
    fun `커플 밖의 사람을 결제자로 지정할 수 없다`() {
        val outsider = userRepository.save(Fixtures.user(nickname = "남"))

        assertThatThrownBy { expenseService.create(couple.ownerId, request(payerId = outsider.requiredId)) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYER_NOT_IN_COUPLE)
    }

    @Test
    fun `파트너가 아직 없으면 지출을 기록할 수 없다`() {
        val (soloUserId, _) = coupleFixture.pending("혼자유저")

        assertThatThrownBy { expenseService.create(soloUserId, request(payerId = soloUserId)) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPLE_NOT_ACTIVE)
    }

    @Test
    fun `커플이 없는 사용자는 지출을 기록할 수 없다`() {
        val loner = userRepository.save(Fixtures.user())

        assertThatThrownBy { expenseService.create(loner.requiredId, request(payerId = loner.requiredId)) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPLE_NOT_FOUND)
    }

    @Test
    fun `파트너도 상대가 등록한 지출을 수정할 수 있다`() {
        val created = expenseService.create(couple.ownerId, request(amount = 30_000))

        val updated = expenseService.update(
            couple.partnerId,
            created.expenseId,
            ExpenseUpdateRequest(amount = 50_000, memo = "금액 정정"),
        )

        assertThat(updated.amount).isEqualTo(50_000)
        assertThat(updated.memo).isEqualTo("금액 정정")
        // 건드리지 않은 값은 그대로다
        assertThat(updated.category).isEqualTo(ExpenseCategory.GROCERY)
        assertThat(updated.payerBurdenRate).isEqualTo(50)
    }

    @Test
    fun `부담 비율만 바꾸면 몫이 다시 계산된다`() {
        val created = expenseService.create(couple.ownerId, request(amount = 100_000, rate = 50))

        val updated = expenseService.update(
            couple.ownerId,
            created.expenseId,
            ExpenseUpdateRequest(payerBurdenRate = 30),
        )

        assertThat(updated.payerShare).isEqualTo(30_000)
        assertThat(updated.partnerShare).isEqualTo(70_000)
    }

    @Test
    fun `삭제하면 목록에서 사라지고 다시 조회되지 않는다`() {
        val created = expenseService.create(couple.ownerId, request())

        expenseService.delete(couple.ownerId, created.expenseId)

        assertThatThrownBy { expenseService.get(couple.ownerId, created.expenseId) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPENSE_NOT_FOUND)
        // 행 자체는 남는다 (소프트 삭제)
        assertThat(expenseRepository.findAll()).hasSize(1)
        assertThat(expenseRepository.findAll().first().isDeleted).isTrue()
    }

    @Test
    fun `이미 삭제된 지출은 다시 수정할 수 없다`() {
        val created = expenseService.create(couple.ownerId, request())
        expenseService.delete(couple.ownerId, created.expenseId)

        assertThatThrownBy {
            expenseService.update(couple.ownerId, created.expenseId, ExpenseUpdateRequest(amount = 1_000))
        }.isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPENSE_NOT_FOUND)
    }

    @Test
    fun `다른 커플의 지출은 조회도 수정도 할 수 없다`() {
        val created = expenseService.create(couple.ownerId, request())
        val other = coupleFixture.active("A", "B")

        assertThatThrownBy { expenseService.get(other.ownerId, created.expenseId) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPENSE_NOT_FOUND)

        assertThatThrownBy {
            expenseService.update(other.ownerId, created.expenseId, ExpenseUpdateRequest(amount = 1))
        }.isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPENSE_NOT_FOUND)
    }

    @Test
    fun `빈 메모는 저장하지 않는다`() {
        val created = expenseService.create(couple.ownerId, request(memo = "   "))

        assertThat(created.memo).isNull()
    }

    @Test
    fun `이미 확정된 달에는 지출을 새로 넣을 수 없다`() {
        confirmJanuary()

        assertThatThrownBy {
            expenseService.create(couple.ownerId, request(spentAt = LocalDate.of(2026, 1, 20)))
        }.isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERIOD_ALREADY_SETTLED)
    }

    @Test
    fun `확정된 달로 지출을 옮겨 넣을 수도 없다`() {
        val february = expenseService.create(couple.ownerId, request(spentAt = LocalDate.of(2026, 2, 10)))
        confirmJanuary()

        assertThatThrownBy {
            expenseService.update(
                couple.ownerId,
                february.expenseId,
                ExpenseUpdateRequest(spentAt = LocalDate.of(2026, 1, 20)),
            )
        }.isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERIOD_ALREADY_SETTLED)
    }

    @Test
    fun `확정되지 않은 달은 그대로 기록할 수 있다`() {
        confirmJanuary()

        val february = expenseService.create(couple.ownerId, request(spentAt = LocalDate.of(2026, 2, 10)))

        assertThat(february.expenseId).isPositive()
    }

    /** 정산 확정 자체는 W5-6 범위라, 여기서는 확정된 상태만 만들어 두고 차단 규칙만 검증한다. */
    private fun confirmJanuary() {
        val target = coupleRepository.findById(couple.coupleId).orElseThrow()
        val ownerId = couple.ownerId
        val partnerId = couple.partnerId

        val settlement = Settlement(couple = target, period = YearMonth.of(2026, 1)).apply {
            updateBalance(ownerId, partnerId, 15_000)
            confirm(ownerId, mutableClock.instant())
        }
        settlementRepository.saveAndFlush(settlement)
    }
}

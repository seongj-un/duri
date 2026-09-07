package com.duri.expense

import com.duri.expense.application.BurdenPresetService
import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.BurdenPresetUpdateRequest
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("카테고리별 부담비율 프리셋")
class BurdenPresetServiceTest(
    private val presetService: BurdenPresetService,
    private val expenseService: ExpenseService,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active(ownerNickname = "성준", partnerNickname = "지현")
    }

    private fun setPreset(category: ExpenseCategory, userId: Long, rate: Int) =
        presetService.update(
            couple.ownerId,
            BurdenPresetUpdateRequest(listOf(BurdenPresetUpdateRequest.Item(category, userId, rate))),
        )

    @Test
    fun `설정 전에는 모든 카테고리가 반반으로 보인다`() {
        val presets = presetService.getAll(couple.ownerId)

        assertThat(presets).hasSize(ExpenseCategory.entries.size)
        assertThat(presets).allMatch { !it.customized }
        assertThat(presets).allMatch { preset -> preset.rates.all { it.burdenRate == 50 } }
    }

    @Test
    fun `한 사람의 비율만 정하면 나머지는 자동으로 채워진다`() {
        val presets = setPreset(ExpenseCategory.RENT, couple.ownerId, 30)

        val rent = presets.first { it.category == ExpenseCategory.RENT }
        assertThat(rent.customized).isTrue()
        assertThat(rent.rates.first { it.member.userId == couple.ownerId }.burdenRate).isEqualTo(30)
        assertThat(rent.rates.first { it.member.userId == couple.partnerId }.burdenRate).isEqualTo(70)
        assertThat(rent.rates.sumOf { it.burdenRate }).isEqualTo(100)
    }

    @Test
    fun `어느 쪽을 기준으로 보내도 같은 결과가 된다`() {
        setPreset(ExpenseCategory.RENT, couple.ownerId, 30)
        val viaOwner = presetService.getAll(couple.ownerId).first { it.category == ExpenseCategory.RENT }

        setPreset(ExpenseCategory.RENT, couple.partnerId, 70)
        val viaPartner = presetService.getAll(couple.ownerId).first { it.category == ExpenseCategory.RENT }

        assertThat(viaPartner.rates).isEqualTo(viaOwner.rates)
    }

    @Test
    fun `여러 카테고리를 한 번에 저장할 수 있다`() {
        val presets = presetService.update(
            couple.ownerId,
            BurdenPresetUpdateRequest(
                listOf(
                    BurdenPresetUpdateRequest.Item(ExpenseCategory.RENT, couple.ownerId, 30),
                    BurdenPresetUpdateRequest.Item(ExpenseCategory.DATE, couple.ownerId, 100),
                ),
            ),
        )

        assertThat(presets.filter { it.customized }.map { it.category })
            .containsExactlyInAnyOrder(ExpenseCategory.RENT, ExpenseCategory.DATE)
    }

    @Test
    fun `다시 저장하면 값이 갈아끼워진다`() {
        setPreset(ExpenseCategory.RENT, couple.ownerId, 30)

        val presets = setPreset(ExpenseCategory.RENT, couple.ownerId, 40)

        val rent = presets.first { it.category == ExpenseCategory.RENT }
        assertThat(rent.rates.first { it.member.userId == couple.ownerId }.burdenRate).isEqualTo(40)
    }

    @Test
    fun `지출 등록에서 비율을 생략하면 프리셋을 따른다`() {
        setPreset(ExpenseCategory.RENT, couple.ownerId, 30)

        val expense = expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(
                payerId = couple.ownerId,
                amount = 1_000_000,
                category = ExpenseCategory.RENT,
                payerBurdenRate = null,
                spentAt = LocalDate.of(2026, 1, 1),
                memo = null,
            ),
        )

        assertThat(expense.payerBurdenRate).isEqualTo(30)
        assertThat(expense.payerShare).isEqualTo(300_000)
        assertThat(expense.partnerShare).isEqualTo(700_000)
    }

    @Test
    fun `프리셋은 사람 기준이라 누가 결제해도 부담이 같다`() {
        // 월세는 성준 30 / 지현 70 로 정했다
        setPreset(ExpenseCategory.RENT, couple.ownerId, 30)

        val paidByOwner = expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(couple.ownerId, 1_000_000, ExpenseCategory.RENT, null, LocalDate.of(2026, 1, 1), null),
        )
        val paidByPartner = expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(couple.partnerId, 1_000_000, ExpenseCategory.RENT, null, LocalDate.of(2026, 1, 2), null),
        )

        // 성준이 결제 -> 성준(결제자)이 30% 부담
        assertThat(paidByOwner.payerShare).isEqualTo(300_000)
        // 지현이 결제 -> 지현(결제자)이 70% 부담. 성준 몫은 여전히 30%
        assertThat(paidByPartner.payerShare).isEqualTo(700_000)
        assertThat(paidByPartner.partnerShare).isEqualTo(300_000)
    }

    @Test
    fun `명시적으로 비율을 주면 프리셋보다 우선한다`() {
        setPreset(ExpenseCategory.RENT, couple.ownerId, 30)

        val expense = expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(couple.ownerId, 1_000_000, ExpenseCategory.RENT, 50, LocalDate.of(2026, 1, 1), null),
        )

        assertThat(expense.payerBurdenRate).isEqualTo(50)
    }

    @Test
    fun `프리셋이 없는 카테고리는 반반으로 등록된다`() {
        setPreset(ExpenseCategory.RENT, couple.ownerId, 30)

        val expense = expenseService.create(
            couple.ownerId,
            ExpenseCreateRequest(couple.ownerId, 10_000, ExpenseCategory.DATE, null, LocalDate.of(2026, 1, 1), null),
        )

        assertThat(expense.payerBurdenRate).isEqualTo(50)
    }
}

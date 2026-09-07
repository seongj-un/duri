package com.duri.expense.application

import com.duri.couple.application.CoupleContext
import com.duri.couple.application.CoupleContextLoader
import com.duri.expense.domain.CategoryBurdenPreset
import com.duri.expense.domain.CategoryBurdenPresetRepository
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.BurdenPresetResponse
import com.duri.expense.dto.BurdenPresetUpdateRequest
import com.duri.expense.dto.MemberBurdenRate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카테고리별 기본 부담 비율.
 *
 * 저장은 1번 자리(OWNER) 기준이고, 지출을 만들 때 결제자 기준으로 뒤집어 준다.
 * 사람 기준으로 저장해야 "월세는 성준 30%"가 카드 명의와 무관하게 유지된다.
 */
@Service
class BurdenPresetService(
    private val presetRepository: CategoryBurdenPresetRepository,
    private val coupleContextLoader: CoupleContextLoader,
) {

    @Transactional(readOnly = true)
    fun getAll(userId: Long): List<BurdenPresetResponse> {
        val context = coupleContextLoader.loadActive(userId)
        return toResponses(context, presetRepository.findAllByCoupleId(context.coupleId))
    }

    @Transactional
    fun update(userId: Long, request: BurdenPresetUpdateRequest): List<BurdenPresetResponse> {
        val context = coupleContextLoader.loadActive(userId)
        val member1Id = context.member1.user.requiredId

        request.presets.forEach { item ->
            val category = requireNotNull(item.category)
            val targetUserId = requireNotNull(item.userId)
            val rate = requireNotNull(item.burdenRate)
            context.requirePayer(targetUserId)

            // 어느 쪽을 기준으로 보냈든 1번 자리 기준으로 정규화해 저장한다
            val member1Rate = if (targetUserId == member1Id) rate else CategoryBurdenPreset.FULL - rate

            presetRepository.findByCoupleIdAndCategory(context.coupleId, category)
                ?.changeRate(member1Rate.toShort())
                ?: presetRepository.save(
                    CategoryBurdenPreset(context.coupleId, category, member1Rate.toShort()),
                )
        }

        return toResponses(context, presetRepository.findAllByCoupleId(context.coupleId))
    }

    /**
     * 지출을 만들 때 쓸 결제자 기준 비율.
     * 프리셋이 없으면 반반이다.
     */
    @Transactional(readOnly = true)
    fun payerBurdenRateFor(context: CoupleContext, category: ExpenseCategory, payerId: Long): Short {
        val preset = presetRepository.findByCoupleIdAndCategory(context.coupleId, category)
            ?: return CategoryBurdenPreset.DEFAULT_RATE

        return if (payerId == context.member1.user.requiredId) preset.member1BurdenRate
        else preset.member2BurdenRate
    }

    private fun toResponses(
        context: CoupleContext,
        presets: List<CategoryBurdenPreset>,
    ): List<BurdenPresetResponse> {
        val byCategory = presets.associateBy { it.category }
        val member1Id = context.member1.user.requiredId

        // 저장된 것만 주면 프론트가 나머지 카테고리를 따로 채워야 한다. 전부 내려준다.
        return ExpenseCategory.entries.map { category ->
            val preset = byCategory[category]
            val member1Rate = preset?.member1BurdenRate ?: CategoryBurdenPreset.DEFAULT_RATE

            BurdenPresetResponse(
                category = category,
                categoryName = category.displayName,
                customized = preset != null,
                rates = context.members.map { member ->
                    val memberId = member.user.requiredId
                    MemberBurdenRate(
                        member = context.memberRefOf(memberId),
                        burdenRate = if (memberId == member1Id) {
                            member1Rate.toInt()
                        } else {
                            CategoryBurdenPreset.FULL - member1Rate
                        },
                    )
                },
            )
        }
    }
}

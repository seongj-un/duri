package com.duri.couple.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.domain.Couple
import com.duri.couple.domain.CoupleMember

/**
 * "요청한 사람이 속한, 두 사람이 다 찬 커플" 한 벌.
 *
 * 지출·정산은 전부 이 문맥 위에서 동작하므로 매번 같은 검사를 반복하지 않도록 묶었다.
 */
data class CoupleContext(
    val couple: Couple,
    val members: List<CoupleMember>,
) {
    val coupleId: Long get() = couple.requiredId

    fun memberOf(userId: Long): CoupleMember =
        members.firstOrNull { it.user.requiredId == userId }
            ?: throw BusinessException(ErrorCode.NOT_COUPLE_MEMBER)

    /** 결제자는 두 사람 중 한 명이어야 한다. */
    fun requirePayer(payerId: Long) {
        if (members.none { it.user.requiredId == payerId }) {
            throw BusinessException(ErrorCode.PAYER_NOT_IN_COUPLE)
        }
    }

    fun partnerOf(userId: Long): CoupleMember =
        members.firstOrNull { it.user.requiredId != userId }
            ?: throw BusinessException(ErrorCode.COUPLE_NOT_ACTIVE)
}

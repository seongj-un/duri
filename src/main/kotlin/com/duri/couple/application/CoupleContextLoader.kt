package com.duri.couple.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.domain.CoupleMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CoupleContextLoader(
    private val coupleMemberRepository: CoupleMemberRepository,
) {

    /** 두 사람이 연결된 커플에서만 지출·정산이 가능하다. */
    @Transactional(readOnly = true)
    fun loadActive(userId: Long): CoupleContext {
        val membership = coupleMemberRepository.findActiveByUserId(userId)
            ?: throw BusinessException(ErrorCode.COUPLE_NOT_FOUND)

        val couple = membership.couple
        couple.requireActive()

        return CoupleContext(
            couple = couple,
            members = coupleMemberRepository.findActiveMembersByCoupleId(couple.requiredId),
        )
    }
}

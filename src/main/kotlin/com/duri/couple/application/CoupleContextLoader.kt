package com.duri.couple.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.domain.CoupleMemberRepository
import com.duri.couple.domain.CoupleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CoupleContextLoader(
    private val coupleMemberRepository: CoupleMemberRepository,
    private val coupleRepository: CoupleRepository,
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

    /** 스케줄러처럼 "요청한 사람"이 없는 경로에서 커플 id 로 바로 연다. */
    @Transactional(readOnly = true)
    fun loadByCoupleId(coupleId: Long): CoupleContext {
        val couple = coupleRepository.findById(coupleId).orElseThrow {
            BusinessException(ErrorCode.COUPLE_NOT_FOUND)
        }
        couple.requireActive()

        return CoupleContext(
            couple = couple,
            members = coupleMemberRepository.findActiveMembersByCoupleId(coupleId),
        )
    }
}

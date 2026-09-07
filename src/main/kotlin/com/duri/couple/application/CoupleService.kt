package com.duri.couple.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.domain.Couple
import com.duri.couple.domain.CoupleMember
import com.duri.couple.domain.CoupleMemberRepository
import com.duri.couple.domain.CoupleRepository
import com.duri.couple.dto.CoupleCreateRequest
import com.duri.couple.dto.CoupleMemberResponse
import com.duri.couple.dto.CoupleResponse
import com.duri.couple.dto.CoupleUpdateRequest
import com.duri.user.domain.User
import com.duri.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CoupleService(
    private val coupleRepository: CoupleRepository,
    private val coupleMemberRepository: CoupleMemberRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    /**
     * 커플 space 를 만들고 만든 사람이 1번 자리에 들어간다.
     * 파트너가 초대를 수락하기 전까지는 PENDING 상태다.
     */
    @Transactional
    fun create(userId: Long, request: CoupleCreateRequest): CoupleResponse {
        requireNotInAnyCouple(userId)

        val user = findUser(userId)
        val couple = coupleRepository.save(
            Couple(name = request.name.trim(), createdBy = userId),
        )
        val owner = coupleMemberRepository.save(
            CoupleMember.owner(couple, user, clock.instant()),
        )
        return CoupleResponse(
            coupleId = couple.requiredId,
            name = couple.name,
            status = couple.status,
            settlementDay = couple.settlementDay.toInt(),
            members = listOf(CoupleMemberResponse.from(owner)),
            createdAt = couple.createdAt,
        )
    }

    /** 내가 속한 커플 space. 없으면 404. */
    @Transactional(readOnly = true)
    fun getMyCouple(userId: Long): CoupleResponse {
        val couple = requireActiveMembership(userId).couple
        return toResponse(couple)
    }

    @Transactional
    fun update(userId: Long, request: CoupleUpdateRequest): CoupleResponse {
        val couple = requireActiveMembership(userId).couple
        request.name?.let(couple::rename)
        request.settlementDay?.let(couple::changeSettlementDay)
        return toResponse(couple)
    }

    /** 커플 소속을 전제로 하는 다른 도메인(지출·정산)에서 재사용한다. */
    @Transactional(readOnly = true)
    fun requireActiveMembership(userId: Long): CoupleMember =
        coupleMemberRepository.findActiveByUserId(userId)
            ?: throw BusinessException(ErrorCode.COUPLE_NOT_FOUND)

    private fun requireNotInAnyCouple(userId: Long) {
        if (coupleMemberRepository.findActiveByUserId(userId) != null) {
            throw BusinessException(ErrorCode.ALREADY_IN_COUPLE)
        }
    }

    private fun findUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

    private fun toResponse(couple: Couple): CoupleResponse {
        val members = coupleMemberRepository.findActiveMembersByCoupleId(couple.requiredId)
        return CoupleResponse(
            coupleId = couple.requiredId,
            name = couple.name,
            status = couple.status,
            settlementDay = couple.settlementDay.toInt(),
            members = members.map(CoupleMemberResponse::from),
            createdAt = couple.createdAt,
        )
    }
}

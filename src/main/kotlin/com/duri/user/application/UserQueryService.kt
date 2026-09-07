package com.duri.user.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.domain.CoupleMemberRepository
import com.duri.user.domain.UserRepository
import com.duri.user.dto.MeResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserQueryService(
    private val userRepository: UserRepository,
    private val coupleMemberRepository: CoupleMemberRepository,
) {

    @Transactional(readOnly = true)
    fun getMe(userId: Long): MeResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        val coupleId = coupleMemberRepository.findActiveByUserId(userId)?.couple?.requiredId
        return MeResponse.of(user, coupleId)
    }
}

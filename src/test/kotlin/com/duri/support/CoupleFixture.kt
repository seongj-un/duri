package com.duri.support

import com.duri.couple.application.CoupleInviteService
import com.duri.couple.application.CoupleService
import com.duri.couple.dto.CoupleCreateRequest
import com.duri.user.domain.UserRepository
import org.springframework.boot.test.context.TestComponent

/** 지출·정산 테스트는 대부분 "두 사람이 연결된 커플"에서 시작한다. */
@TestComponent
class CoupleFixture(
    private val userRepository: UserRepository,
    private val coupleService: CoupleService,
    private val inviteService: CoupleInviteService,
) {

    fun active(
        ownerNickname: String = "성준",
        partnerNickname: String = "지현",
        name: String = "우리집 가계부",
    ): ActiveCouple {
        val owner = userRepository.save(Fixtures.user(nickname = ownerNickname))
        val partner = userRepository.save(Fixtures.user(nickname = partnerNickname))

        val couple = coupleService.create(owner.requiredId, CoupleCreateRequest(name))
        val invite = inviteService.issue(owner.requiredId)
        inviteService.accept(partner.requiredId, invite.token)

        return ActiveCouple(
            coupleId = couple.coupleId,
            ownerId = owner.requiredId,
            partnerId = partner.requiredId,
        )
    }

    /** 파트너 없이 혼자인 상태(PENDING). 지출을 막는지 확인할 때 쓴다. */
    fun pending(ownerNickname: String = "성준"): Pair<Long, Long> {
        val owner = userRepository.save(Fixtures.user(nickname = ownerNickname))
        val couple = coupleService.create(owner.requiredId, CoupleCreateRequest("혼자"))
        return owner.requiredId to couple.coupleId
    }
}

data class ActiveCouple(
    val coupleId: Long,
    val ownerId: Long,
    val partnerId: Long,
)

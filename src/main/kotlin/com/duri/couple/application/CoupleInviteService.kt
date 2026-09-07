package com.duri.couple.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.common.support.Hashes
import com.duri.couple.config.InviteProperties
import com.duri.couple.domain.CoupleInvite
import com.duri.couple.domain.CoupleInviteRepository
import com.duri.couple.domain.CoupleMember
import com.duri.couple.domain.CoupleMemberRepository
import com.duri.couple.dto.InviteAcceptResponse
import com.duri.couple.dto.InvitePreviewResponse
import com.duri.couple.dto.InviteResponse
import com.duri.user.domain.User
import com.duri.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CoupleInviteService(
    private val inviteRepository: CoupleInviteRepository,
    private val coupleMemberRepository: CoupleMemberRepository,
    private val userRepository: UserRepository,
    private val properties: InviteProperties,
    private val clock: Clock,
) {

    /**
     * 초대 링크 발급. 커플당 살아있는 초대는 하나뿐이라, 다시 발급하면 이전 링크는 즉시 무효가 된다.
     */
    @Transactional
    fun issue(userId: Long): InviteResponse {
        val couple = coupleMemberRepository.findActiveByUserId(userId)?.couple
            ?: throw BusinessException(ErrorCode.COUPLE_NOT_FOUND)
        if (couple.isActive) throw BusinessException(ErrorCode.COUPLE_ALREADY_FULL)

        val now = clock.instant()

        // uq_couple_invites_active 는 (accepted_at IS NULL AND revoked_at IS NULL) 부분 유니크다.
        // Hibernate 는 flush 시 INSERT 를 UPDATE 보다 먼저 내보내므로,
        // 폐기(UPDATE)를 먼저 DB 에 반영해야 새 초대 INSERT 가 제약에 걸리지 않는다.
        inviteRepository.findByCoupleIdAndAcceptedAtIsNullAndRevokedAtIsNull(couple.requiredId)
            ?.let {
                it.revoke(now)
                inviteRepository.flush()
            }

        val rawToken = Hashes.randomToken()
        val invite = inviteRepository.save(
            CoupleInvite.issue(
                couple = couple,
                inviterId = userId,
                tokenHash = Hashes.sha256(rawToken),
                now = now,
                expiresAt = now.plus(properties.ttl),
            ),
        )

        return InviteResponse(
            token = rawToken,
            inviteUrl = "${properties.baseUrl.trimEnd('/')}/$rawToken",
            expiresAt = invite.expiresAt,
        )
    }

    /** 링크를 받은 사람이 로그인 전에 보는 화면. 토큰 자체가 열람 권한이다. */
    @Transactional(readOnly = true)
    fun preview(rawToken: String): InvitePreviewResponse {
        val invite = inviteRepository.findByTokenHash(Hashes.sha256(rawToken))
            ?: throw BusinessException(ErrorCode.INVITE_NOT_FOUND)
        val inviter = findUser(invite.inviterId)

        return InvitePreviewResponse(
            coupleName = invite.couple.name,
            inviterNickname = inviter.nickname,
            status = invite.statusAt(clock.instant()),
            expiresAt = invite.expiresAt,
        )
    }

    /**
     * 초대 수락 → 2번 자리에 합류하고 커플이 ACTIVE 가 된다.
     *
     * 동시 수락은 세 겹으로 막는다.
     *  1) 초대 행 비관적 락으로 같은 링크의 동시 처리를 직렬화
     *  2) 이미 소진된 초대면 엔티티가 INVITE_ALREADY_USED 를 던짐
     *  3) 그래도 뚫리면 (couple_id, member_no) 유니크 제약이 최종 차단
     */
    @Transactional
    fun accept(userId: Long, rawToken: String): InviteAcceptResponse {
        if (coupleMemberRepository.findActiveByUserId(userId) != null) {
            throw BusinessException(ErrorCode.ALREADY_IN_COUPLE)
        }

        val invite = inviteRepository.findByTokenHashForUpdate(Hashes.sha256(rawToken))
            ?: throw BusinessException(ErrorCode.INVITE_NOT_FOUND)

        val now = clock.instant()
        invite.accept(userId, now)

        val couple = invite.couple
        couple.activate()

        coupleMemberRepository.save(CoupleMember.partner(couple, findUser(userId), now))

        return InviteAcceptResponse(coupleId = couple.requiredId)
    }

    private fun findUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
}

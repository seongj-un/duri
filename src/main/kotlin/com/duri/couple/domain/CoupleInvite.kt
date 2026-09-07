package com.duri.couple.domain

import com.duri.common.entity.BaseEntity
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * 파트너 페어링 초대.
 *
 * 원문 토큰은 링크로 상대에게만 전달되고, 서버는 SHA-256 해시만 저장한다.
 * DB 가 유출되어도 저장된 값으로는 초대를 수락할 수 없다.
 */
@Entity
@Table(name = "couple_invites")
class CoupleInvite(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "couple_id", nullable = false, updatable = false)
    val couple: Couple,

    @Column(name = "inviter_id", nullable = false, updatable = false)
    val inviterId: Long,

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) : BaseEntity() {

    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null
        protected set

    @Column(name = "accepted_by")
    var acceptedBy: Long? = null
        protected set

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
        protected set

    fun statusAt(now: Instant): InviteStatus = when {
        revokedAt != null -> InviteStatus.REVOKED
        acceptedAt != null -> InviteStatus.ACCEPTED
        !now.isBefore(expiresAt) -> InviteStatus.EXPIRED
        else -> InviteStatus.PENDING
    }

    /**
     * 초대를 수락한다. 상태 검증까지 엔티티가 책임진다.
     * 실제 "누가 어느 자리에 들어가는지"는 서비스가 DB 제약과 함께 처리한다.
     */
    fun accept(accepterId: Long, now: Instant) {
        when (statusAt(now)) {
            InviteStatus.REVOKED -> throw BusinessException(ErrorCode.INVITE_REVOKED)
            InviteStatus.ACCEPTED -> throw BusinessException(ErrorCode.INVITE_ALREADY_USED)
            InviteStatus.EXPIRED -> throw BusinessException(ErrorCode.INVITE_EXPIRED)
            InviteStatus.PENDING -> Unit
        }
        if (accepterId == inviterId) throw BusinessException(ErrorCode.CANNOT_INVITE_SELF)

        acceptedAt = now
        acceptedBy = accepterId
    }

    fun revoke(now: Instant) {
        if (statusAt(now) != InviteStatus.PENDING) return
        revokedAt = now
    }

    companion object {
        fun issue(
            couple: Couple,
            inviterId: Long,
            tokenHash: String,
            now: Instant,
            expiresAt: Instant,
        ) = CoupleInvite(
            couple = couple,
            inviterId = inviterId,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            createdAt = now,
        )
    }
}

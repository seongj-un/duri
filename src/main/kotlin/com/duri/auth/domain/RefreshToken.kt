package com.duri.auth.domain

import com.duri.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 리프레시 토큰 한 건. 원문은 쿠키로만 오가고 서버는 SHA-256 해시만 저장한다.
 *
 * 재발급마다 새 행을 만들고 이전 행에 [usedAt] 을 찍는다(회전).
 * 이미 사용된 토큰이 다시 들어오면 탈취로 보고 같은 [familyId] 전체를 폐기한다.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "family_id", nullable = false, updatable = false)
    val familyId: UUID,

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) : BaseEntity() {

    @Column(name = "used_at")
    var usedAt: Instant? = null
        protected set

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
        protected set

    val isUsed: Boolean get() = usedAt != null
    val isRevoked: Boolean get() = revokedAt != null

    fun isExpiredAt(now: Instant): Boolean = !now.isBefore(expiresAt)

    /** 회전 시 이전 토큰을 소진 처리한다. */
    fun markUsed(now: Instant) {
        if (usedAt == null) usedAt = now
    }

    fun revoke(now: Instant) {
        if (revokedAt == null) revokedAt = now
    }
}

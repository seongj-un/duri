package com.duri.auth.application

import com.duri.auth.config.JwtProperties
import com.duri.auth.domain.RefreshToken
import com.duri.auth.domain.RefreshTokenRepository
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.common.support.Hashes
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val properties: JwtProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 최초 로그인. 새 회전 체인(family)을 시작한다. */
    @Transactional
    fun issueNewFamily(userId: Long): IssuedRefreshToken = create(userId, UUID.randomUUID())

    /**
     * 리프레시 토큰 회전.
     *
     * 이미 사용된 토큰이 다시 들어오면 탈취로 간주하고 같은 family 전체를 폐기한다.
     * 그 폐기는 요청이 거절되더라도 반드시 남아야 하므로 BusinessException 에는 롤백을 걸지 않는다.
     */
    @Transactional(noRollbackFor = [BusinessException::class])
    fun rotate(rawToken: String): IssuedRefreshToken {
        val now = clock.instant()
        val stored = repository.findByTokenHash(Hashes.sha256(rawToken))
            ?: throw BusinessException(ErrorCode.REFRESH_TOKEN_INVALID)

        if (stored.isUsed) {
            val revoked = repository.revokeFamily(stored.familyId, now)
            log.warn(
                "refresh token reuse detected: userId={} familyId={} revokedCount={}",
                stored.userId, stored.familyId, revoked,
            )
            throw BusinessException(ErrorCode.REFRESH_TOKEN_REUSED)
        }
        if (stored.isRevoked || stored.isExpiredAt(now)) {
            throw BusinessException(ErrorCode.REFRESH_TOKEN_INVALID)
        }

        stored.markUsed(now)
        return create(stored.userId, stored.familyId)
    }

    /** 이 기기에서만 로그아웃. 토큰이 이미 못 쓰는 상태여도 조용히 넘어간다. */
    @Transactional
    fun revoke(rawToken: String) {
        val now = clock.instant()
        repository.findByTokenHash(Hashes.sha256(rawToken))
            ?.let { repository.revokeFamily(it.familyId, now) }
    }

    /** 모든 기기에서 로그아웃. */
    @Transactional
    fun revokeAll(userId: Long) {
        repository.revokeAllOfUser(userId, clock.instant())
    }

    private fun create(userId: Long, familyId: UUID): IssuedRefreshToken {
        val now = clock.instant()
        val raw = Hashes.randomToken()
        val entity = repository.save(
            RefreshToken(
                userId = userId,
                familyId = familyId,
                tokenHash = Hashes.sha256(raw),
                expiresAt = now.plus(properties.refreshTokenTtl),
                createdAt = now,
            ),
        )
        return IssuedRefreshToken(rawValue = raw, userId = userId, expiresAt = entity.expiresAt)
    }
}

data class IssuedRefreshToken(
    /** 클라이언트에게 단 한 번 전달되는 원문. 서버는 해시만 보관한다. */
    val rawValue: String,
    val userId: Long,
    val expiresAt: java.time.Instant,
)

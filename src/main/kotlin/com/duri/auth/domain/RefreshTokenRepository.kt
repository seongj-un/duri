package com.duri.auth.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /** 탈취 의심 시 회전 체인 전체를 한 번에 끊는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update RefreshToken t
        set t.revokedAt = :now
        where t.familyId = :familyId and t.revokedAt is null
        """,
    )
    fun revokeFamily(@Param("familyId") familyId: UUID, @Param("now") now: Instant): Int

    /** 로그아웃(전체 기기). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update RefreshToken t
        set t.revokedAt = :now
        where t.userId = :userId and t.revokedAt is null
        """,
    )
    fun revokeAllOfUser(@Param("userId") userId: Long, @Param("now") now: Instant): Int

    /** 만료된 지 오래된 행 정리용. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :threshold")
    fun deleteExpiredBefore(@Param("threshold") threshold: Instant): Int
}

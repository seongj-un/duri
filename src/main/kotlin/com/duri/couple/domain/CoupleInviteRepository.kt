package com.duri.couple.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoupleInviteRepository : JpaRepository<CoupleInvite, Long> {

    /**
     * 초대 수락 경로. 두 사람이 같은 링크를 동시에 열어도 한 명만 통과해야 하므로
     * 행 단위 비관적 락으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select i from CoupleInvite i
        join fetch i.couple
        where i.tokenHash = :tokenHash
        """,
    )
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): CoupleInvite?

    /** 미리보기(수락 전 "누구의 초대인지" 확인)용. 락 없이 읽는다. */
    @Query(
        """
        select i from CoupleInvite i
        join fetch i.couple
        where i.tokenHash = :tokenHash
        """,
    )
    fun findByTokenHash(@Param("tokenHash") tokenHash: String): CoupleInvite?

    /** 커플당 살아있는 초대는 하나뿐이다 (uq_couple_invites_active). */
    fun findByCoupleIdAndAcceptedAtIsNullAndRevokedAtIsNull(coupleId: Long): CoupleInvite?
}

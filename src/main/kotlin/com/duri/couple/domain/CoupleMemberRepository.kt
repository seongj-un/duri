package com.duri.couple.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoupleMemberRepository : JpaRepository<CoupleMember, Long> {

    /** 한 사용자는 활성 커플을 최대 하나만 가진다 (uq_couple_members_active_user). */
    @Query(
        """
        select m from CoupleMember m
        join fetch m.couple
        where m.user.id = :userId and m.leftAt is null
        """,
    )
    fun findActiveByUserId(@Param("userId") userId: Long): CoupleMember?

    /** 커플 상세 화면용. 두 사람의 프로필까지 한 번에 가져와 N+1 을 피한다. */
    @Query(
        """
        select m from CoupleMember m
        join fetch m.user
        where m.couple.id = :coupleId and m.leftAt is null
        order by m.memberNo asc
        """,
    )
    fun findActiveMembersByCoupleId(@Param("coupleId") coupleId: Long): List<CoupleMember>

    fun existsByCoupleIdAndUserIdAndLeftAtIsNull(coupleId: Long, userId: Long): Boolean
}

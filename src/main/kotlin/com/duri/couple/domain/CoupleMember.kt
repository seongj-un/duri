package com.duri.couple.domain

import com.duri.common.entity.BaseEntity
import com.duri.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * 커플 구성원.
 *
 * [memberNo] 는 space 안의 자리 번호(1|2)다. DB 의 (couple_id, member_no) 유니크 제약과
 * 짝을 이루어 "세 번째 사람"이 동시 요청으로 끼어드는 것을 막는다.
 */
@Entity
@Table(name = "couple_members")
class CoupleMember(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "couple_id", nullable = false, updatable = false)
    val couple: Couple,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    val user: User,

    @Column(name = "member_no", nullable = false, updatable = false)
    val memberNo: Short,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20, updatable = false)
    val role: MemberRole,

    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant,
) : BaseEntity() {

    @Column(name = "left_at")
    var leftAt: Instant? = null
        protected set

    val isActive: Boolean get() = leftAt == null

    fun leave(now: Instant) {
        if (leftAt == null) leftAt = now
    }

    companion object {
        const val OWNER_SLOT: Short = 1
        const val PARTNER_SLOT: Short = 2

        fun owner(couple: Couple, user: User, now: Instant) =
            CoupleMember(couple, user, OWNER_SLOT, MemberRole.OWNER, now)

        fun partner(couple: Couple, user: User, now: Instant) =
            CoupleMember(couple, user, PARTNER_SLOT, MemberRole.PARTNER, now)
    }
}

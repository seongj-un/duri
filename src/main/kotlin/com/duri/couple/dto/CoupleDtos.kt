package com.duri.couple.dto

import com.duri.couple.domain.CoupleMember
import com.duri.couple.domain.CoupleStatus
import com.duri.couple.domain.MemberRole
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CoupleCreateRequest(
    @field:NotBlank(message = "커플 space 이름을 입력해 주세요.")
    @field:Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    val name: String,
)

data class CoupleUpdateRequest(
    @field:Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    val name: String? = null,

    @field:Min(value = 1, message = "정산 기준일은 1일 이상이어야 합니다.")
    @field:Max(value = 28, message = "정산 기준일은 28일 이하여야 합니다.")
    val settlementDay: Int? = null,
)

data class CoupleResponse(
    val coupleId: Long,
    val name: String,
    val status: CoupleStatus,
    val settlementDay: Int,
    val members: List<CoupleMemberResponse>,
    val createdAt: Instant,
)

data class CoupleMemberResponse(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val role: MemberRole,
    val memberNo: Int,
    val joinedAt: Instant,
) {
    companion object {
        fun from(member: CoupleMember) = CoupleMemberResponse(
            userId = member.user.requiredId,
            nickname = member.user.nickname,
            profileImageUrl = member.user.profileImageUrl,
            role = member.role,
            memberNo = member.memberNo.toInt(),
            joinedAt = member.joinedAt,
        )
    }
}

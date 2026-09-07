package com.duri.user.dto

import com.duri.user.domain.AuthProvider
import com.duri.user.domain.User

/**
 * 앱을 켤 때 프론트가 가장 먼저 호출하는 부트스트랩 응답.
 * coupleId 가 null 이면 온보딩(커플 생성 또는 초대 수락)으로 보낸다.
 */
data class MeResponse(
    val userId: Long,
    val nickname: String,
    val email: String?,
    val profileImageUrl: String?,
    val provider: AuthProvider,
    val coupleId: Long?,
) {
    companion object {
        fun of(user: User, coupleId: Long?) = MeResponse(
            userId = user.requiredId,
            nickname = user.nickname,
            email = user.email,
            profileImageUrl = user.profileImageUrl,
            provider = user.provider,
            coupleId = coupleId,
        )
    }
}

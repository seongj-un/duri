package com.duri.support

import com.duri.user.domain.AuthProvider
import com.duri.user.domain.User
import java.util.concurrent.atomic.AtomicLong

object Fixtures {

    private val sequence = AtomicLong(1)

    fun user(
        nickname: String = "테스트유저",
        provider: AuthProvider = AuthProvider.KAKAO,
        providerId: String = "provider-${sequence.getAndIncrement()}",
        email: String? = null,
    ): User = User.register(
        provider = provider,
        providerId = providerId,
        nickname = nickname,
        email = email,
        profileImageUrl = null,
    )
}

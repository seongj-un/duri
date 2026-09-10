package com.duri.support

import com.duri.user.domain.User
import java.util.concurrent.atomic.AtomicLong

object Fixtures {

    private val sequence = AtomicLong(1)

    /**
     * 인증을 거치지 않고 사용자를 직접 만든다.
     * password_hash 는 NOT NULL 이라 형태만 맞는 더미를 넣는다. 이 계정으로 로그인하지는 않는다.
     */
    fun user(
        nickname: String = "테스트유저",
        email: String = "user-${sequence.getAndIncrement()}@example.com",
    ): User = User.register(
        email = email,
        passwordHash = DUMMY_HASH,
        nickname = nickname,
    )

    private const val DUMMY_HASH = "\$2a\$10\$0123456789012345678901234567890123456789012345678901"
}

package com.duri.user.domain

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode

enum class AuthProvider {
    KAKAO,
    GOOGLE,
    ;

    companion object {
        /** Spring Security 의 registrationId("kakao"/"google") 와 매핑한다. */
        fun from(registrationId: String): AuthProvider =
            entries.firstOrNull { it.name.equals(registrationId, ignoreCase = true) }
                ?: throw BusinessException(
                    ErrorCode.UNSUPPORTED_OAUTH_PROVIDER,
                    "지원하지 않는 로그인 제공자입니다: $registrationId",
                )
    }
}

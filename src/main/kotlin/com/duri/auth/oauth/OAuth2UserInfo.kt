package com.duri.auth.oauth

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.user.domain.AuthProvider

/**
 * 제공자마다 다른 응답 형태를 하나로 정규화한다.
 *
 * 카카오는 사용자가 동의하지 않은 항목이 통째로 빠져서 오므로
 * 식별자(id)를 제외한 모든 값을 nullable 로 다룬다.
 */
data class OAuth2UserInfo(
    val provider: AuthProvider,
    val providerId: String,
    val nickname: String?,
    val email: String?,
    val profileImageUrl: String?,
) {
    companion object {

        fun from(registrationId: String, attributes: Map<String, Any?>): OAuth2UserInfo =
            when (val provider = AuthProvider.from(registrationId)) {
                AuthProvider.KAKAO -> ofKakao(provider, attributes)
                AuthProvider.GOOGLE -> ofGoogle(provider, attributes)
            }

        /**
         * { "id": 1234, "kakao_account": { "email": ..., "profile": { "nickname": ..., "profile_image_url": ... } } }
         */
        private fun ofKakao(provider: AuthProvider, attributes: Map<String, Any?>): OAuth2UserInfo {
            val account = attributes.child("kakao_account")
            val profile = account.child("profile")
            return OAuth2UserInfo(
                provider = provider,
                providerId = attributes["id"]?.toString().requireProviderId(),
                nickname = profile["nickname"] as? String,
                email = account["email"] as? String,
                profileImageUrl = profile["profile_image_url"] as? String,
            )
        }

        /** { "sub": "...", "name": "...", "email": "...", "picture": "..." } */
        private fun ofGoogle(provider: AuthProvider, attributes: Map<String, Any?>) = OAuth2UserInfo(
            provider = provider,
            providerId = (attributes["sub"] as? String).requireProviderId(),
            nickname = attributes["name"] as? String,
            email = attributes["email"] as? String,
            profileImageUrl = attributes["picture"] as? String,
        )

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.child(key: String): Map<String, Any?> =
            this[key] as? Map<String, Any?> ?: emptyMap()

        private fun String?.requireProviderId(): String =
            this?.takeIf { it.isNotBlank() }
                ?: throw BusinessException(
                    ErrorCode.OAUTH_PROFILE_UNAVAILABLE,
                    "소셜 계정의 고유 식별자를 받지 못했습니다.",
                )
    }
}

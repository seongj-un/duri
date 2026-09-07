package com.duri.user.application

import com.duri.auth.oauth.OAuth2UserInfo
import com.duri.user.domain.User
import com.duri.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserRegistrationService(
    private val userRepository: UserRepository,
) {

    /**
     * 소셜 로그인 결과를 우리 사용자에 연결한다. 처음 보는 계정이면 가입시킨다.
     *
     * 동시 최초 로그인 경합은 OAuth 인가 코드가 1회용이라 실제로는 발생하지 않고,
     * 혹시 뚫리더라도 uq_users_provider_identity 가 막는다.
     */
    @Transactional
    fun findOrRegister(info: OAuth2UserInfo): Registration {
        val existing = userRepository.findByProviderAndProviderId(info.provider, info.providerId)
        if (existing != null) {
            existing.syncProfile(info.nickname, info.profileImageUrl, info.email)
            return Registration(userId = existing.requiredId, isNewUser = false)
        }

        val created = userRepository.save(
            User.register(
                provider = info.provider,
                providerId = info.providerId,
                nickname = info.nickname.orEmpty(),
                email = info.email,
                profileImageUrl = info.profileImageUrl,
            ),
        )
        return Registration(userId = created.requiredId, isNewUser = true)
    }

    data class Registration(
        val userId: Long,
        val isNewUser: Boolean,
    )
}

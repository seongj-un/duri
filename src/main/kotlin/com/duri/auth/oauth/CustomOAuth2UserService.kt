package com.duri.auth.oauth

import com.duri.common.error.BusinessException
import com.duri.user.application.UserRegistrationService
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

/**
 * 제공자에게서 프로필을 받아온 뒤 우리 사용자로 연결(없으면 가입)한다.
 */
@Service
class CustomOAuth2UserService(
    private val userRegistrationService: UserRegistrationService,
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val delegate = super.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId

        // 도메인 예외를 그대로 던지면 Spring Security 가 500 으로 처리한다.
        // 실패 핸들러가 프론트로 되돌려 보낼 수 있도록 OAuth2AuthenticationException 으로 감싼다.
        val result = try {
            val info = OAuth2UserInfo.from(registrationId, delegate.attributes)
            userRegistrationService.findOrRegister(info)
        } catch (e: BusinessException) {
            throw OAuth2AuthenticationException(OAuth2Error(e.errorCode.code, e.message, null), e)
        }

        return DuriOAuth2User(
            userId = result.userId,
            isNewUser = result.isNewUser,
            attributes = delegate.attributes,
        )
    }
}

package com.duri.auth.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.user.domain.User
import com.duri.user.domain.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 이메일 + 비밀번호로 신원을 확인한다.
 *
 * 토큰 발급은 이 클래스의 일이 아니다. 여기서 확인한 사용자를 받아
 * AuthController 가 기존 AccessTokenIssuer / RefreshTokenService 에 넘긴다.
 */
@Service
class LocalAuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun signup(email: String, rawPassword: String, nickname: String): User {
        val normalized = normalize(email)
        if (userRepository.existsByEmail(normalized)) {
            throw BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        return userRepository.save(
            User.register(
                email = normalized,
                // 인코더 시그니처가 널을 허용할 뿐, 실제로 널을 돌려주지는 않는다.
                passwordHash = requireNotNull(passwordEncoder.encode(rawPassword)),
                nickname = nickname,
            ),
        )
    }

    /**
     * 실패 이유를 구분하지 않는다.
     * "그런 이메일은 없다" 와 "비밀번호가 틀렸다" 를 나눠 알려주면 가입 여부가 새어나간다.
     */
    @Transactional(readOnly = true)
    fun authenticate(email: String, rawPassword: String): User {
        val user = userRepository.findByEmail(normalize(email))
            ?: throw BusinessException(ErrorCode.INVALID_CREDENTIALS)

        if (!passwordEncoder.matches(rawPassword, user.passwordHash)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }
        return user
    }

    /** 대소문자만 다른 이메일로 계정이 둘 생기지 않게 한다. */
    private fun normalize(email: String) = email.trim().lowercase()
}

package com.duri.auth

import com.duri.auth.application.LocalAuthService
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.support.IntegrationTestBase
import com.duri.user.domain.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("자체 로그인")
class LocalAuthServiceTest(
    private val localAuthService: LocalAuthService,
    private val userRepository: UserRepository,
) : IntegrationTestBase() {

    @Test
    fun `가입하면 사용자가 저장되고 비밀번호는 평문으로 남지 않는다`() {
        val user = localAuthService.signup("seongjun@example.com", "password123", "성준")

        val saved = userRepository.findById(user.requiredId).orElseThrow()
        assertThat(saved.email).isEqualTo("seongjun@example.com")
        assertThat(saved.nickname).isEqualTo("성준")
        assertThat(saved.passwordHash).isNotEqualTo("password123")
        assertThat(saved.passwordHash).startsWith("$2")
    }

    @Test
    fun `같은 이메일로 다시 가입할 수 없다`() {
        localAuthService.signup("seongjun@example.com", "password123", "성준")

        assertThatThrownBy { localAuthService.signup("seongjun@example.com", "another123", "다른사람") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS)
    }

    @Test
    fun `이메일의 대소문자와 앞뒤 공백은 구분하지 않는다`() {
        val signedUp = localAuthService.signup("Seongjun@Example.com ", "password123", "성준")
        assertThat(signedUp.email).isEqualTo("seongjun@example.com")

        val authenticated = localAuthService.authenticate(" SEONGJUN@example.COM", "password123")
        assertThat(authenticated.requiredId).isEqualTo(signedUp.requiredId)
    }

    @Test
    fun `이메일과 비밀번호가 맞으면 그 사용자를 돌려준다`() {
        val signedUp = localAuthService.signup("seongjun@example.com", "password123", "성준")

        val authenticated = localAuthService.authenticate("seongjun@example.com", "password123")

        assertThat(authenticated.requiredId).isEqualTo(signedUp.requiredId)
    }

    @Test
    fun `비밀번호가 틀리면 자격증명 오류를 던진다`() {
        localAuthService.signup("seongjun@example.com", "password123", "성준")

        assertThatThrownBy { localAuthService.authenticate("seongjun@example.com", "wrong-password") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INVALID_CREDENTIALS)
    }

    @Test
    fun `없는 이메일도 비밀번호가 틀린 것과 똑같은 오류를 던진다`() {
        assertThatThrownBy { localAuthService.authenticate("nobody@example.com", "password123") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INVALID_CREDENTIALS)
    }
}

package com.duri.auth

import com.duri.auth.application.RefreshTokenService
import com.duri.auth.domain.RefreshTokenRepository
import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.support.Fixtures
import com.duri.support.IntegrationTestBase
import com.duri.user.domain.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

@DisplayName("리프레시 토큰 회전과 재사용 탐지")
class RefreshTokenRotationTest(
    @Autowired private val refreshTokenService: RefreshTokenService,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val userRepository: UserRepository,
) : IntegrationTestBase() {

    @Test
    fun `회전하면 새 토큰이 나오고 이전 토큰은 소진된다`() {
        val userId = userRepository.save(Fixtures.user()).requiredId
        val first = refreshTokenService.issueNewFamily(userId)

        val second = refreshTokenService.rotate(first.rawValue)

        assertThat(second.rawValue).isNotEqualTo(first.rawValue)
        assertThat(second.userId).isEqualTo(userId)
        assertThat(refreshTokenRepository.findAll()).hasSize(2)
        assertThat(refreshTokenRepository.findAll().count { it.isUsed }).isEqualTo(1)
    }

    @Test
    fun `회전은 같은 family 를 유지한다`() {
        val userId = userRepository.save(Fixtures.user()).requiredId
        val first = refreshTokenService.issueNewFamily(userId)

        refreshTokenService.rotate(first.rawValue)

        assertThat(refreshTokenRepository.findAll().map { it.familyId }.distinct()).hasSize(1)
    }

    @Test
    fun `이미 사용한 토큰을 다시 쓰면 재사용으로 보고 family 전체를 폐기한다`() {
        val userId = userRepository.save(Fixtures.user()).requiredId
        val first = refreshTokenService.issueNewFamily(userId)
        val second = refreshTokenService.rotate(first.rawValue)

        // 탈취범이 가로챈 옛 토큰으로 재발급을 시도한다
        assertThatThrownBy { refreshTokenService.rotate(first.rawValue) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_REUSED)

        // 폐기는 예외가 던져져도 커밋되어야 한다
        assertThat(refreshTokenRepository.findAll()).allMatch { it.isRevoked }

        // 정상 사용자가 들고 있던 최신 토큰도 함께 끊긴다 (재로그인 유도)
        assertThatThrownBy { refreshTokenService.rotate(second.rawValue) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_INVALID)
    }

    @Test
    fun `만료된 토큰으로는 재발급할 수 없다`() {
        val userId = userRepository.save(Fixtures.user()).requiredId
        val issued = refreshTokenService.issueNewFamily(userId)

        mutableClock.advance(Duration.ofDays(15))

        assertThatThrownBy { refreshTokenService.rotate(issued.rawValue) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_INVALID)
    }

    @Test
    fun `없는 토큰으로는 재발급할 수 없다`() {
        assertThatThrownBy { refreshTokenService.rotate("존재하지-않는-토큰") }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_INVALID)
    }

    @Test
    fun `로그아웃하면 그 토큰의 family 가 끊긴다`() {
        val userId = userRepository.save(Fixtures.user()).requiredId
        val issued = refreshTokenService.issueNewFamily(userId)

        refreshTokenService.revoke(issued.rawValue)

        assertThatThrownBy { refreshTokenService.rotate(issued.rawValue) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_INVALID)
    }

    @Test
    fun `다른 기기의 로그인은 별개 family 라 서로 영향이 없다`() {
        val userId = userRepository.save(Fixtures.user()).requiredId
        val phone = refreshTokenService.issueNewFamily(userId)
        val laptop = refreshTokenService.issueNewFamily(userId)

        refreshTokenService.revoke(phone.rawValue)

        // 노트북 세션은 계속 살아 있어야 한다
        val rotated = refreshTokenService.rotate(laptop.rawValue)
        assertThat(rotated.userId).isEqualTo(userId)
    }
}

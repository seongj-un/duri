package com.duri.couple

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.couple.application.CoupleInviteService
import com.duri.couple.application.CoupleService
import com.duri.couple.domain.CoupleStatus
import com.duri.couple.domain.InviteStatus
import com.duri.couple.domain.MemberRole
import com.duri.couple.dto.CoupleCreateRequest
import com.duri.support.Fixtures
import com.duri.support.IntegrationTestBase
import com.duri.user.domain.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

@DisplayName("커플 space 생성과 파트너 페어링")
class CouplePairingTest(
    @Autowired private val coupleService: CoupleService,
    @Autowired private val inviteService: CoupleInviteService,
    @Autowired private val userRepository: UserRepository,
) : IntegrationTestBase() {

    @Test
    fun `space 를 만들면 만든 사람이 1번 자리 OWNER 로 들어가고 PENDING 상태다`() {
        val owner = userRepository.save(Fixtures.user(nickname = "성준"))

        val couple = coupleService.create(owner.requiredId, CoupleCreateRequest("우리집 가계부"))

        assertThat(couple.name).isEqualTo("우리집 가계부")
        assertThat(couple.status).isEqualTo(CoupleStatus.PENDING)
        assertThat(couple.settlementDay).isEqualTo(1)
        assertThat(couple.members).singleElement().satisfies({
            assertThat(it.userId).isEqualTo(owner.requiredId)
            assertThat(it.role).isEqualTo(MemberRole.OWNER)
            assertThat(it.memberNo).isEqualTo(1)
        })
    }

    @Test
    fun `초대를 수락하면 두 사람이 연결되고 ACTIVE 가 된다`() {
        val owner = userRepository.save(Fixtures.user(nickname = "성준"))
        val partner = userRepository.save(Fixtures.user(nickname = "지현"))
        coupleService.create(owner.requiredId, CoupleCreateRequest("우리집 가계부"))
        val invite = inviteService.issue(owner.requiredId)

        val accepted = inviteService.accept(partner.requiredId, invite.token)

        val couple = coupleService.getMyCouple(partner.requiredId)
        assertThat(accepted.coupleId).isEqualTo(couple.coupleId)
        assertThat(couple.status).isEqualTo(CoupleStatus.ACTIVE)
        assertThat(couple.members).hasSize(2)
        assertThat(couple.members.map { it.nickname }).containsExactly("성준", "지현")
        assertThat(couple.members.map { it.role })
            .containsExactly(MemberRole.OWNER, MemberRole.PARTNER)
    }

    @Test
    fun `이미 커플이 있으면 새 space 를 만들 수 없다`() {
        val owner = userRepository.save(Fixtures.user())
        coupleService.create(owner.requiredId, CoupleCreateRequest("첫 번째"))

        assertThatThrownBy { coupleService.create(owner.requiredId, CoupleCreateRequest("두 번째")) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_IN_COUPLE)
    }

    @Test
    fun `같은 초대 링크를 두 번 수락할 수 없다`() {
        val owner = userRepository.save(Fixtures.user())
        val partner = userRepository.save(Fixtures.user())
        val third = userRepository.save(Fixtures.user())
        coupleService.create(owner.requiredId, CoupleCreateRequest("우리집"))
        val invite = inviteService.issue(owner.requiredId)
        inviteService.accept(partner.requiredId, invite.token)

        assertThatThrownBy { inviteService.accept(third.requiredId, invite.token) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_ALREADY_USED)
    }

    @Test
    fun `본인이 만든 초대는 본인이 수락할 수 없다`() {
        val owner = userRepository.save(Fixtures.user())
        coupleService.create(owner.requiredId, CoupleCreateRequest("우리집"))
        val invite = inviteService.issue(owner.requiredId)

        assertThatThrownBy { inviteService.accept(owner.requiredId, invite.token) }
            .isInstanceOf(BusinessException::class.java)
            // 이미 커플에 속해 있으므로 그 검사에 먼저 걸린다
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_IN_COUPLE)
    }

    @Test
    fun `초대를 다시 발급하면 이전 링크는 무효가 된다`() {
        val owner = userRepository.save(Fixtures.user())
        val partner = userRepository.save(Fixtures.user())
        coupleService.create(owner.requiredId, CoupleCreateRequest("우리집"))
        val first = inviteService.issue(owner.requiredId)

        val second = inviteService.issue(owner.requiredId)

        assertThat(second.token).isNotEqualTo(first.token)
        assertThat(inviteService.preview(first.token).status).isEqualTo(InviteStatus.REVOKED)
        assertThatThrownBy { inviteService.accept(partner.requiredId, first.token) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_REVOKED)
    }

    @Test
    fun `만료된 초대는 수락할 수 없다`() {
        val owner = userRepository.save(Fixtures.user())
        val partner = userRepository.save(Fixtures.user())
        coupleService.create(owner.requiredId, CoupleCreateRequest("우리집"))
        val invite = inviteService.issue(owner.requiredId)

        mutableClock.advance(Duration.ofDays(8))

        assertThatThrownBy { inviteService.accept(partner.requiredId, invite.token) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_EXPIRED)
    }

    @Test
    fun `두 사람이 다 찬 space 는 새 초대를 발급할 수 없다`() {
        val owner = userRepository.save(Fixtures.user())
        val partner = userRepository.save(Fixtures.user())
        coupleService.create(owner.requiredId, CoupleCreateRequest("우리집"))
        inviteService.accept(partner.requiredId, inviteService.issue(owner.requiredId).token)

        assertThatThrownBy { inviteService.issue(owner.requiredId) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPLE_ALREADY_FULL)
    }

    @Test
    fun `초대 미리보기는 초대한 사람과 space 이름만 보여준다`() {
        val owner = userRepository.save(Fixtures.user(nickname = "성준"))
        coupleService.create(owner.requiredId, CoupleCreateRequest("우리집 가계부"))
        val invite = inviteService.issue(owner.requiredId)

        val preview = inviteService.preview(invite.token)

        assertThat(preview.inviterNickname).isEqualTo("성준")
        assertThat(preview.coupleName).isEqualTo("우리집 가계부")
        assertThat(preview.status).isEqualTo(InviteStatus.PENDING)
    }
}

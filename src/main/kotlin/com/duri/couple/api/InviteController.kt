package com.duri.couple.api

import com.duri.common.web.CurrentUserId
import com.duri.couple.application.CoupleInviteService
import com.duri.couple.dto.InviteAcceptResponse
import com.duri.couple.dto.InvitePreviewResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/invites")
class InviteController(
    private val inviteService: CoupleInviteService,
) {

    /**
     * 링크를 열었을 때 보여줄 정보. 로그인 없이 호출할 수 있다.
     * 토큰을 가진 사람만 접근할 수 있고, 초대한 사람의 닉네임과 space 이름만 노출한다.
     */
    @GetMapping("/{token}")
    fun preview(@PathVariable token: String): InvitePreviewResponse = inviteService.preview(token)

    @PostMapping("/{token}/accept")
    fun accept(
        @CurrentUserId userId: Long,
        @PathVariable token: String,
    ): InviteAcceptResponse = inviteService.accept(userId, token)
}

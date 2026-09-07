package com.duri.couple.api

import com.duri.common.web.CurrentUserId
import com.duri.couple.application.CoupleInviteService
import com.duri.couple.application.CoupleService
import com.duri.couple.dto.CoupleCreateRequest
import com.duri.couple.dto.CoupleResponse
import com.duri.couple.dto.CoupleUpdateRequest
import com.duri.couple.dto.InviteResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/couples")
class CoupleController(
    private val coupleService: CoupleService,
    private val inviteService: CoupleInviteService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CoupleCreateRequest,
    ): CoupleResponse = coupleService.create(userId, request)

    @GetMapping("/me")
    fun getMyCouple(@CurrentUserId userId: Long): CoupleResponse = coupleService.getMyCouple(userId)

    @PatchMapping("/me")
    fun update(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CoupleUpdateRequest,
    ): CoupleResponse = coupleService.update(userId, request)

    /** 파트너 초대 링크 발급. 다시 호출하면 이전 링크는 무효가 된다. */
    @PostMapping("/me/invites")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueInvite(@CurrentUserId userId: Long): InviteResponse = inviteService.issue(userId)
}

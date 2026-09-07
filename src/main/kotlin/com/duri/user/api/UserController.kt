package com.duri.user.api

import com.duri.common.web.CurrentUserId
import com.duri.user.application.UserQueryService
import com.duri.user.dto.MeResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userQueryService: UserQueryService,
) {

    @GetMapping("/me")
    fun getMe(@CurrentUserId userId: Long): MeResponse = userQueryService.getMe(userId)
}

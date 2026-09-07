package com.duri.user.api

import com.duri.common.web.CurrentUserId
import com.duri.user.application.AccountService
import com.duri.user.dto.AccountRequest
import com.duri.user.dto.AccountResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 정산할 때 상대에게 보여줄 내 입금 계좌. */
@RestController
@RequestMapping("/api/v1/users/me/account")
class AccountController(
    private val accountService: AccountService,
) {

    @GetMapping
    fun get(@CurrentUserId userId: Long): AccountResponse = accountService.get(userId)

    @PutMapping
    fun upsert(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: AccountRequest,
    ): AccountResponse = accountService.upsert(userId, request)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUserId userId: Long) = accountService.delete(userId)
}

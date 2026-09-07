package com.duri.expense.api

import com.duri.common.web.CurrentUserId
import com.duri.expense.application.BurdenPresetService
import com.duri.expense.dto.BurdenPresetResponse
import com.duri.expense.dto.BurdenPresetUpdateRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 카테고리별 기본 부담 비율. 지출 등록 때 비율을 생략하면 여기를 따른다. */
@RestController
@RequestMapping("/api/v1/couples/me/burden-presets")
class BurdenPresetController(
    private val burdenPresetService: BurdenPresetService,
) {

    /** 저장된 것만이 아니라 전체 카테고리를 내려준다. 저장 전이면 반반으로 보인다. */
    @GetMapping
    fun getAll(@CurrentUserId userId: Long): List<BurdenPresetResponse> =
        burdenPresetService.getAll(userId)

    @PutMapping
    fun update(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: BurdenPresetUpdateRequest,
    ): List<BurdenPresetResponse> = burdenPresetService.update(userId, request)
}

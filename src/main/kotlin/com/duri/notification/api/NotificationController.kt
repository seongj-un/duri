package com.duri.notification.api

import com.duri.common.web.CurrentUserId
import com.duri.notification.application.NotificationService
import com.duri.notification.dto.NotificationPageResponse
import com.duri.notification.dto.NotificationResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {

    @GetMapping
    fun list(
        @CurrentUserId userId: Long,
        @PageableDefault(size = 20) pageable: Pageable,
    ): NotificationPageResponse = notificationService.list(userId, pageable)

    @PostMapping("/{notificationId}/read")
    fun markRead(
        @CurrentUserId userId: Long,
        @PathVariable notificationId: Long,
    ): NotificationResponse = notificationService.markRead(userId, notificationId)

    @PostMapping("/read-all")
    fun markAllRead(@CurrentUserId userId: Long): Map<String, Int> =
        mapOf("updated" to notificationService.markAllRead(userId))
}

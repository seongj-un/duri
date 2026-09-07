package com.duri.notification.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.notification.domain.NotificationRepository
import com.duri.notification.dto.NotificationPageResponse
import com.duri.notification.dto.NotificationResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun list(userId: Long, pageable: Pageable): NotificationPageResponse {
        val page = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
        return NotificationPageResponse(
            content = page.content.map(NotificationResponse::of),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            hasNext = page.hasNext(),
            unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId),
        )
    }

    @Transactional
    fun markRead(userId: Long, notificationId: Long): NotificationResponse {
        val notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다.")
        notification.markRead(clock.instant())
        return NotificationResponse.of(notification)
    }

    /** 모두 읽음. 이미 읽은 것은 건드리지 않는다. */
    @Transactional
    fun markAllRead(userId: Long): Int = notificationRepository.markAllRead(userId, clock.instant())
}

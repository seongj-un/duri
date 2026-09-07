package com.duri.notification.dto

import com.duri.notification.domain.Notification
import com.duri.notification.domain.NotificationType
import java.time.Instant

data class NotificationResponse(
    val notificationId: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    /** "2026-08". 관련 정산 기간이 없으면 null. */
    val period: String?,
    val read: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun of(notification: Notification) = NotificationResponse(
            notificationId = notification.requiredId,
            type = notification.type,
            title = notification.title,
            body = notification.body,
            // 저장은 YYYYMM, 응답은 사람이 읽는 형식으로 맞춘다
            period = notification.period?.let { "${it.substring(0, 4)}-${it.substring(4)}" },
            read = notification.isRead,
            createdAt = notification.createdAt,
        )
    }
}

data class NotificationPageResponse(
    val content: List<NotificationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
    /** 배지에 띄울 안 읽은 개수. 페이지와 무관한 전체 기준. */
    val unreadCount: Long,
)

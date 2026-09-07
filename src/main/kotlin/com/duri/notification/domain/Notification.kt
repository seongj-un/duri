package com.duri.notification.domain

import com.duri.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "couple_id", nullable = false, updatable = false)
    val coupleId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    val type: NotificationType,

    @Column(name = "title", nullable = false, length = 100, updatable = false)
    val title: String,

    @Column(name = "body", nullable = false, length = 255, updatable = false)
    val body: String,

    @Column(name = "period", length = 6, updatable = false)
    val period: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) : BaseEntity() {

    @Column(name = "read_at")
    var readAt: Instant? = null
        protected set

    val isRead: Boolean get() = readAt != null

    fun markRead(now: Instant) {
        if (readAt == null) readAt = now
    }

    companion object {
        private val PERIOD_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMM")

        fun settlementReminder(
            userId: Long,
            coupleId: Long,
            period: YearMonth,
            title: String,
            body: String,
            now: Instant,
        ) = Notification(
            userId = userId,
            coupleId = coupleId,
            type = NotificationType.SETTLEMENT_REMINDER,
            title = title,
            body = body,
            period = period.format(PERIOD_KEY),
            createdAt = now,
        )
    }
}

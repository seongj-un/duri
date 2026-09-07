package com.duri.notification.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun countByUserIdAndReadAtIsNull(userId: Long): Long

    fun findByIdAndUserId(id: Long, userId: Long): Notification?

    fun existsByUserIdAndTypeAndPeriod(userId: Long, type: NotificationType, period: String): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    fun markAllRead(@Param("userId") userId: Long, @Param("now") now: Instant): Int
}

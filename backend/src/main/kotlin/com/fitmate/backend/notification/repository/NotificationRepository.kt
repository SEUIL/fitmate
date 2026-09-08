package com.fitmate.backend.notification.repository

import com.fitmate.backend.notification.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    @Query("""select n from Notification n where n.userId = :userId
        and (:unreadOnly = false or n.readAt is null)
        and (:cursorAt is null or n.createdAt < :cursorAt or (n.createdAt = :cursorAt and n.id < :cursorId))
        order by n.createdAt desc, n.id desc""")
    fun findPage(
        @Param("userId") userId: UUID, @Param("unreadOnly") unreadOnly: Boolean,
        @Param("cursorAt") cursorAt: Instant?, @Param("cursorId") cursorId: UUID?, pageable: Pageable,
    ): List<Notification>
    fun countByUserIdAndReadAtIsNull(userId: UUID): Long
    fun existsByUserIdAndEventKey(userId: UUID, eventKey: String): Boolean
}

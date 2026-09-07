package com.fitmate.backend.notification.repository

import com.fitmate.backend.notification.domain.UserNotification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<UserNotification, UUID> {
    fun findAllByRecipientUserIdOrderByCreatedAtDesc(recipientUserId: UUID): List<UserNotification>
    fun deleteAllByRecipientUserId(recipientUserId: UUID)
}

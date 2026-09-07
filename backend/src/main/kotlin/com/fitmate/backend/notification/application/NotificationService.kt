package com.fitmate.backend.notification.application

import com.fitmate.backend.member.repository.NotificationSettingRepository
import com.fitmate.backend.notification.domain.NotificationType
import com.fitmate.backend.notification.domain.UserNotification
import com.fitmate.backend.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val notifications: NotificationRepository,
    private val settings: NotificationSettingRepository,
) {
    @Transactional
    fun community(recipientId: UUID, actorId: UUID, type: NotificationType, resourceType: String, resourceId: UUID, body: String) = publish(recipientId, actorId, type, resourceType, resourceId, body, community = true)
    @Transactional
    fun dm(recipientId: UUID, actorId: UUID, type: NotificationType, resourceId: UUID, body: String) = publish(recipientId, actorId, type, "DM_CONVERSATION", resourceId, body, dm = true)
    @Transactional
    fun follow(recipientId: UUID, actorId: UUID, type: NotificationType, body: String) = publish(recipientId, actorId, type, "USER", actorId, body)

    private fun publish(recipientId: UUID, actorId: UUID, type: NotificationType, resourceType: String, resourceId: UUID, body: String, community: Boolean = false, dm: Boolean = false) {
        if (recipientId == actorId) return
        val setting = settings.findById(recipientId).orElse(null) ?: return
        if (!setting.inAppEnabled || (community && !setting.communityEnabled) || (dm && !setting.dmEnabled)) return
        notifications.save(UserNotification(UUID.randomUUID(), recipientId, actorId, type, resourceType, resourceId, body.take(500)))
    }
}

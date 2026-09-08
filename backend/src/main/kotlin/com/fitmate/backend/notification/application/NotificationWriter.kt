package com.fitmate.backend.notification.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.member.repository.NotificationSettingRepository
import com.fitmate.backend.notification.domain.NotificationType
import com.fitmate.backend.notification.domain.UserNotification
import com.fitmate.backend.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationWriter(
    private val settings: NotificationSettingRepository,
    private val notifications: NotificationRepository,
    private val objectMapper: ObjectMapper,
) {
    fun mate(userId: UUID, actorId: UUID?, type: String, eventKey: String, payload: String) {
        if (userId == actorId) return
        val setting = settings.findById(userId).orElse(null) ?: return
        if (!setting.inAppEnabled || !setting.mateEnabled || notifications.existsByRecipientUserIdAndEventKey(userId, eventKey)) return

        val payloadNode = objectMapper.readTree(payload)
        val target = sequenceOf(
            "postId" to "MATE_POST",
            "roomId" to "MATE_CHAT_ROOM",
            "messageId" to "MATE_CHAT_MESSAGE",
            "applicationId" to "MATE_APPLICATION",
        ).firstNotNullOfOrNull { (field, resourceType) ->
            payloadNode.path(field).asText(null)?.let { value -> runCatching { resourceType to UUID.fromString(value) }.getOrNull() }
        }
        notifications.save(UserNotification(
            id = UUID.randomUUID(), recipientUserId = userId, actorUserId = actorId,
            type = NotificationType.valueOf(type), resourceType = target?.first, resourceId = target?.second,
            body = message(type), eventKey = eventKey,
        ))
    }

    private fun message(type: String) = when (type) {
        "MATE_INQUIRY_RECEIVED" -> "새 문의가 도착했습니다."
        "MATE_MESSAGE_RECEIVED" -> "새 메시지가 도착했습니다."
        "MATE_APPLICATION_RECEIVED" -> "새 참가 신청이 도착했습니다."
        "MATE_APPLICATION_ACCEPTED" -> "참가 신청이 수락되었습니다."
        "MATE_APPLICATION_REJECTED" -> "참가 신청이 거절되었습니다."
        "MATE_APPLICATION_CANCELED", "MATE_PARTICIPATION_CANCELED" -> "참가 신청이 취소되었습니다."
        "MATE_PARTICIPATION_WITHDRAWN" -> "참가자가 철회했습니다."
        "MATE_POST_CLOSED" -> "모집이 마감되었습니다."
        "MATE_POST_CANCELED" -> "모집이 취소되었습니다."
        else -> "메이트 활동 알림입니다."
    }
}

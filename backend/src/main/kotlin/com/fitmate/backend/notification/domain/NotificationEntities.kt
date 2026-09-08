package com.fitmate.backend.notification.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class NotificationType {
    FOLLOW_REQUEST, FOLLOW_ACCEPTED, POST_LIKED, POST_COMMENTED, DM_REQUEST, DM_MESSAGE, REPORT_STATUS,
    MATE_INQUIRY_RECEIVED, MATE_MESSAGE_RECEIVED, MATE_APPLICATION_RECEIVED, MATE_APPLICATION_ACCEPTED,
    MATE_APPLICATION_REJECTED, MATE_APPLICATION_CANCELED, MATE_PARTICIPATION_CANCELED,
    MATE_PARTICIPATION_WITHDRAWN, MATE_POST_CLOSED, MATE_POST_CANCELED,
}

@Entity
@Table(name = "user_notifications")
class UserNotification(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "recipient_user_id", columnDefinition = "BINARY(16)") var recipientUserId: UUID? = null,
    @Column(name = "actor_user_id", columnDefinition = "BINARY(16)") var actorUserId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var type: NotificationType = NotificationType.FOLLOW_REQUEST,
    @Column(name = "resource_type", length = 32) var resourceType: String? = null,
    @Column(name = "resource_id", columnDefinition = "BINARY(16)") var resourceId: UUID? = null,
    @Column(length = 500) var body: String? = null,
    @Column(name = "read_at") var readAt: Instant? = null,
    @Column(name = "event_key", length = 255) var eventKey: String? = null,
) : BaseTimeEntity()

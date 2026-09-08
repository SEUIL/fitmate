package com.fitmate.backend.notification.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "core_notifications")
class Notification(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "actor_id", columnDefinition = "BINARY(16)") var actorId: UUID? = null,
    @Column(nullable = false, length = 64) var type: String = "", @Column(name = "event_key", nullable = false, length = 255) var eventKey: String = "",
    @Column(name = "payload_json", nullable = false, columnDefinition = "json") var payloadJson: String = "{}", @Column(name = "read_at") var readAt: Instant? = null,
) : BaseTimeEntity()

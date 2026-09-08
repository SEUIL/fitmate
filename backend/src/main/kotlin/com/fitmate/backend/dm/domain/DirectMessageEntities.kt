package com.fitmate.backend.dm.domain

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

enum class ConversationStatus { PENDING, ACTIVE, REJECTED }

@Entity
@Table(name = "dm_conversations")
class DirectConversation(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "first_user_id", columnDefinition = "BINARY(16)") var firstUserId: UUID? = null,
    @Column(name = "second_user_id", columnDefinition = "BINARY(16)") var secondUserId: UUID? = null,
    @Column(name = "requester_user_id", columnDefinition = "BINARY(16)") var requesterUserId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ConversationStatus = ConversationStatus.PENDING,
    @Column(name = "responded_at") var respondedAt: Instant? = null,
    @Column(name = "last_message_at") var lastMessageAt: Instant? = null,
) : BaseTimeEntity()

@Entity
@Table(name = "dm_messages")
class DirectMessage(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "conversation_id", columnDefinition = "BINARY(16)") var conversationId: UUID? = null,
    @Column(name = "sender_user_id", columnDefinition = "BINARY(16)") var senderUserId: UUID? = null,
    @Column(nullable = false, length = 2000) var content: String = "",
    @Column(name = "media_id", columnDefinition = "BINARY(16)") var mediaId: UUID? = null,
    @Column(name = "read_at") var readAt: Instant? = null,
    @Column(name = "deleted_at") var deletedAt: Instant? = null,
) : BaseTimeEntity()

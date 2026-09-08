package com.fitmate.backend.dm.api

import com.fitmate.backend.dm.domain.ConversationStatus
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateConversationRequest(val recipientUserId: UUID, @field:Size(max = 2000) val content: String = "", val mediaId: UUID? = null)
data class SendDirectMessageRequest(@field:Size(max = 2000) val content: String = "", val mediaId: UUID? = null)
data class ConversationResponse(val conversationId: UUID, val otherUserId: UUID, val status: ConversationStatus, val requesterUserId: UUID, val lastMessageAt: Instant?, val createdAt: Instant)
data class DirectMessageResponse(val messageId: UUID, val senderUserId: UUID, val content: String, val mediaUrl: String?, val readAt: Instant?, val createdAt: Instant)

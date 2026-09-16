package com.fitmate.backend.dm.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.dm.api.ConversationResponse
import com.fitmate.backend.dm.api.CreateConversationRequest
import com.fitmate.backend.dm.api.DirectMessageResponse
import com.fitmate.backend.dm.api.SendDirectMessageRequest
import com.fitmate.backend.dm.domain.ConversationStatus
import com.fitmate.backend.dm.domain.DirectConversation
import com.fitmate.backend.dm.domain.DirectMessage
import com.fitmate.backend.dm.repository.DirectConversationRepository
import com.fitmate.backend.dm.repository.DirectMessageRepository
import com.fitmate.backend.media.application.MediaAssetService
import com.fitmate.backend.media.domain.MediaPurpose
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.social.repository.FollowRepository
import com.fitmate.backend.notification.application.NotificationService
import com.fitmate.backend.notification.domain.NotificationType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DirectMessageService(
    private val conversations: DirectConversationRepository,
    private val messages: DirectMessageRepository,
    private val blocks: BlockRepository,
    private val follows: FollowRepository,
    private val media: MediaAssetService,
    private val notifications: NotificationService,
) {
    @Transactional
    fun start(senderId: UUID, request: CreateConversationRequest): ConversationResponse {
        validateMessage(request.content, request.mediaId)
        requireDifferent(senderId, request.recipientUserId)
        ensureNotBlocked(senderId, request.recipientUserId)
        val (first, second) = canonicalPair(senderId, request.recipientUserId)
        val conversation = conversations.findByFirstUserIdAndSecondUserId(first, second) ?: DirectConversation(
            UUID.randomUUID(), first, second, senderId,
            if (isMutual(senderId, request.recipientUserId)) ConversationStatus.ACTIVE else ConversationStatus.PENDING,
        )
        if (conversation.status == ConversationStatus.REJECTED) {
            conversation.status = ConversationStatus.PENDING
            conversation.requesterUserId = senderId
            conversation.respondedAt = null
        }
        if (conversation.status == ConversationStatus.PENDING && conversation.requesterUserId != senderId) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "상대방의 메시지 요청에 응답해 주세요.")
        }
        val saved = conversations.saveAndFlush(conversation)
        sendInternal(senderId, saved, request.content, request.mediaId)
        notifications.dm(request.recipientUserId, senderId, if (saved.status == ConversationStatus.PENDING) NotificationType.DM_REQUEST else NotificationType.DM_MESSAGE, requireNotNull(saved.id), "새 메시지가 도착했습니다.")
        return conversationResponse(senderId, saved)
    }

    @Transactional
    fun respond(userId: UUID, conversationId: UUID, accepted: Boolean): ConversationResponse {
        val conversation = accessible(userId, conversationId)
        if (conversation.status != ConversationStatus.PENDING || conversation.requesterUserId == userId) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        conversation.status = if (accepted) ConversationStatus.ACTIVE else ConversationStatus.REJECTED
        conversation.respondedAt = Instant.now()
        return conversationResponse(userId, conversations.saveAndFlush(conversation))
    }

    @Transactional(readOnly = true)
    fun conversations(userId: UUID): List<ConversationResponse> = conversations.findAllByFirstUserIdOrSecondUserIdOrderByLastMessageAtDesc(userId, userId)
        .filter { !blocked(userId, otherUser(userId, it)) }.map { conversationResponse(userId, it) }

    @Transactional(readOnly = true)
    fun pendingRequests(userId: UUID): List<ConversationResponse> = conversations.findAllByStatusAndRequesterUserIdNot(ConversationStatus.PENDING, userId)
        .filter { contains(userId, it) && !blocked(userId, otherUser(userId, it)) }.map { conversationResponse(userId, it) }

    @Transactional
    fun send(userId: UUID, conversationId: UUID, request: SendDirectMessageRequest): DirectMessageResponse {
        validateMessage(request.content, request.mediaId)
        val conversation = accessible(userId, conversationId)
        if (conversation.status != ConversationStatus.ACTIVE) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "수락된 대화에서만 메시지를 보낼 수 있습니다.")
        val message = sendInternal(userId, conversation, request.content, request.mediaId)
        notifications.dm(otherUser(userId, conversation), userId, NotificationType.DM_MESSAGE, conversationId, "새 메시지가 도착했습니다.")
        return messageResponse(message)
    }

    @Transactional
    fun messages(userId: UUID, conversationId: UUID): List<DirectMessageResponse> {
        accessible(userId, conversationId)
        val now = Instant.now()
        return messages.findAllByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId).map { message ->
            if (message.senderUserId != userId && message.readAt == null) message.readAt = now
            messageResponse(message)
        }
    }

    @Transactional(readOnly = true)
    fun openMedia(userId: UUID, conversationId: UUID, mediaId: UUID) = media.openForAuthorizedReader(mediaId.also {
        accessible(userId, conversationId)
        if (!messages.existsByConversationIdAndMediaIdAndDeletedAtIsNull(conversationId, mediaId)) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
    })

    private fun sendInternal(senderId: UUID, conversation: DirectConversation, content: String, mediaId: UUID?): DirectMessage {
        mediaId?.let { media.requireOwnedActive(senderId, it, MediaPurpose.DM) }
        val item = messages.saveAndFlush(DirectMessage(UUID.randomUUID(), requireNotNull(conversation.id), senderId, content.trim(), mediaId))
        conversation.lastMessageAt = item.createdAt
        conversations.saveAndFlush(conversation)
        return item
    }

    private fun accessible(userId: UUID, conversationId: UUID): DirectConversation = conversations.findById(conversationId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        .also { if (!contains(userId, it) || blocked(userId, otherUser(userId, it))) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
    private fun contains(userId: UUID, conversation: DirectConversation) = conversation.firstUserId == userId || conversation.secondUserId == userId
    private fun otherUser(userId: UUID, conversation: DirectConversation) = if (conversation.firstUserId == userId) requireNotNull(conversation.secondUserId) else requireNotNull(conversation.firstUserId)
    private fun blocked(first: UUID, second: UUID) = blocks.existsByIdBlockerUserIdAndIdBlockedUserId(first, second) || blocks.existsByIdBlockerUserIdAndIdBlockedUserId(second, first)
    private fun ensureNotBlocked(first: UUID, second: UUID) { if (blocked(first, second)) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
    private fun isMutual(first: UUID, second: UUID) = follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(first, second, FollowStatus.ACCEPTED) && follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(second, first, FollowStatus.ACCEPTED)
    private fun requireDifferent(first: UUID, second: UUID) { if (first == second) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "자기 자신에게 메시지를 보낼 수 없습니다.") }
    private fun validateMessage(content: String, mediaId: UUID?) { if (content.isBlank() && mediaId == null) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "메시지 또는 이미지를 입력해 주세요.") }
    private fun canonicalPair(first: UUID, second: UUID) = if (first.toString() < second.toString()) first to second else second to first
    private fun conversationResponse(viewerId: UUID, item: DirectConversation) = ConversationResponse(
        requireNotNull(item.id), otherUser(viewerId, item), item.status, requireNotNull(item.requesterUserId), item.lastMessageAt, item.createdAt,
        messages.findFirstByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(requireNotNull(item.id))?.let(::messageResponse),
    )
    private fun messageResponse(item: DirectMessage) = DirectMessageResponse(requireNotNull(item.id), requireNotNull(item.senderUserId), item.content, item.mediaId?.let { "/api/v1/dm/conversations/${item.conversationId}/media/$it" }, item.readAt, item.createdAt)
}

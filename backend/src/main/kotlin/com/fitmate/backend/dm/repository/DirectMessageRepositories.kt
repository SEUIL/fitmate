package com.fitmate.backend.dm.repository

import com.fitmate.backend.dm.domain.DirectConversation
import com.fitmate.backend.dm.domain.DirectMessage
import com.fitmate.backend.dm.domain.ConversationStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DirectConversationRepository : JpaRepository<DirectConversation, UUID> {
    fun findByFirstUserIdAndSecondUserId(firstUserId: UUID, secondUserId: UUID): DirectConversation?
    fun findAllByFirstUserIdOrSecondUserIdOrderByLastMessageAtDesc(firstUserId: UUID, secondUserId: UUID): List<DirectConversation>
    fun findAllByStatusAndRequesterUserIdNot(status: ConversationStatus, requesterUserId: UUID): List<DirectConversation>
}

interface DirectMessageRepository : JpaRepository<DirectMessage, UUID> {
    fun findFirstByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(conversationId: UUID): DirectMessage?
    fun findAllByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId: UUID): List<DirectMessage>
    fun existsByConversationIdAndMediaIdAndDeletedAtIsNull(conversationId: UUID, mediaId: UUID): Boolean
    fun existsByMediaIdAndDeletedAtIsNull(mediaId: UUID): Boolean
}

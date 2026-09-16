package com.fitmate.backend.dm.application

import com.fitmate.backend.dm.domain.ConversationStatus
import com.fitmate.backend.dm.domain.DirectConversation
import com.fitmate.backend.dm.domain.DirectMessage
import com.fitmate.backend.dm.repository.DirectConversationRepository
import com.fitmate.backend.dm.repository.DirectMessageRepository
import com.fitmate.backend.media.application.MediaAssetService
import com.fitmate.backend.notification.application.NotificationService
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.social.repository.FollowRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class DirectMessagePreviewTest {
    private val conversations = mock(DirectConversationRepository::class.java)
    private val messages = mock(DirectMessageRepository::class.java)
    private val blocks = mock(BlockRepository::class.java)
    private val service = DirectMessageService(conversations, messages, blocks,
        mock(FollowRepository::class.java), mock(MediaAssetService::class.java), mock(NotificationService::class.java))
    private val viewer = UUID.randomUUID()
    private val peer = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val room = DirectConversation(id, viewer, peer, peer, ConversationStatus.ACTIVE)

    private fun list(message: DirectMessage?) = run {
        `when`(conversations.findAllByFirstUserIdOrSecondUserIdOrderByLastMessageAtDesc(viewer, viewer)).thenReturn(listOf(room))
        `when`(messages.findFirstByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(id)).thenReturn(message)
        service.conversations(viewer)
    }

    @Test fun `list returns latest visible message without marking it read`() {
        val message = DirectMessage(UUID.randomUUID(), id, peer, "내일 8시에 봬요!")
        val preview = list(message).single().lastMessage!!
        assertEquals(message.content, preview.content)
        assertEquals(message.id, preview.messageId)
        assertNull(message.readAt)
        assertNull(preview.readAt)
        verify(messages).findFirstByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(id)
        verifyNoMoreInteractions(messages)
    }

    @Test fun `photo preview retains media information`() {
        val mediaId = UUID.randomUUID()
        val preview = list(DirectMessage(UUID.randomUUID(), id, peer, "", mediaId)).single().lastMessage!!
        assertEquals("/api/v1/dm/conversations/$id/media/$mediaId", preview.mediaUrl)
    }

    @Test fun `empty conversation has no last message`() {
        assertNull(list(null).single().lastMessage)
    }

    @Test fun `blocked conversation exposes no preview`() {
        `when`(blocks.existsByIdBlockerUserIdAndIdBlockedUserId(viewer, peer)).thenReturn(true)
        assertTrue(list(null).isEmpty())
        verifyNoInteractions(messages)
    }
}

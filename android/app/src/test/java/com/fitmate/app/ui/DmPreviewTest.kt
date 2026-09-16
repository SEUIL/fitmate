package com.fitmate.app.ui

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DmPreviewTest {
    private fun state(extra: String): SocialState = SocialState(emptyList(), emptyList()).also {
        it.replaceServerConversations(listOf(JsonParser.parseString(
            """{"conversationId":"room","otherUserId":"peer","status":"ACTIVE","otherUserNickname":"김민준",$extra}"""
        ).asJsonObject))
    }

    @Test fun `last message appears before opening room without loading fake chat history`() {
        val state = state(""""lastMessage":{"content":"저도 5분 뒤 도착해요!","mediaUrl":null}""")
        val room = state.chats.single()
        assertEquals("저도 5분 뒤 도착해요!", conversationPreview(room, false))
        assertTrue(room.messages.isEmpty())
        assertEquals(0, room.unread)
        val restored = SocialState(emptyList(), emptyList())
        state.snapshot().restore(restored)
        assertEquals(room.serverPreview, restored.chats.single().serverPreview)
    }

    @Test fun `photo message has photo preview`() {
        val room = state(""""lastMessage":{"content":"","mediaUrl":"/media/photo"}""").chats.single()
        assertEquals("사진", conversationPreview(room, false))
    }

    @Test fun `only empty conversations show first message prompt`() {
        val room = state(""""lastMessage":null""").chats.single()
        assertEquals("첫 메시지를 보내보세요.", conversationPreview(room, false))
        assertEquals("메시지 요청을 확인해 주세요.", conversationPreview(room, true))
    }

    @Test fun `legacy server with prior messages does not imply an empty chat`() {
        val room = state("\"lastMessageAt\":\"2026-09-16T10:00:00Z\"").chats.single()
        assertEquals("메시지를 확인해 보세요.", conversationPreview(room, false))
    }

    @Test fun `newly sent message takes precedence over list preview`() {
        val room = state(""""lastMessage":{"content":"이전 메시지"}""").chats.single()
        room.messages = listOf(ChatEntry(1, "새 메시지", true))
        assertEquals("새 메시지", conversationPreview(room, false))
    }
}

package com.fitmate.app.ui

import com.fitmate.app.data.remote.ApiFailure
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.ServerSocialApi
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.*
import org.junit.Test

class DmParticipantNameTest {
    private fun withApi(profileStatus: Int = 200, test: (ServerSocialApi, () -> Int) -> Unit) {
        var profileReads = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1") { exchange ->
            var status = 200
            val response = when {
                exchange.requestURI.path.endsWith("login") ->
                    """{"data":{"tokens":{"accessToken":"fixture","refreshToken":"fixture"}}}"""
                exchange.requestURI.path.endsWith("conversations") ->
                    """{"data":{"items":[{"conversationId":"first","otherUserId":"peer","status":"ACTIVE"},{"conversationId":"second","otherUserId":"peer","status":"ACTIVE"}]}}"""
                else -> {
                    profileReads++
                    status = profileStatus
                    if (status == 200) """{"data":{"userId":"peer","nickname":"김민준"}}"""
                    else """{"error":{"code":"PROFILE_UNAVAILABLE","message":"Profile unavailable"}}"""
                }
            }
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = FitMateApiClient("http://127.0.0.1:${server.address.port}/api/v1")
            client.authenticate("auth/login", emptyMap<String, String>())
            test(ServerSocialApi(client)) { profileReads }
        } finally { server.stop(0) }
    }

    @Test fun `conversation list and room use actual nickname with one lookup per participant`() = withApi { api, reads ->
        val state = SocialState(emptyList(), emptyList())
        state.replaceServerConversations(api.conversations())
        assertEquals(listOf("김민준", "김민준"), state.chats.map { it.name })
        assertEquals("김민준", state.serverChat("first")?.name)
        assertEquals(1, reads())
    }

    @Test fun `unavailable profile uses neutral label without exposing account id`() {
        for (status in listOf(403, 404)) withApi(status) { api, _ ->
            assertEquals("알 수 없는 사용자", api.conversations().first().get("otherUserNickname").asString)
        }
    }

    @Test fun `server failure is not disguised as a fake nickname`() = withApi(500) { api, _ ->
        assertEquals(500, assertThrows(ApiFailure::class.java) { api.conversations() }.status)
    }

    @Test fun `old responses do not display uuid fragments`() {
        val state = SocialState(emptyList(), emptyList())
        state.replaceServerConversations(listOf(JsonParser.parseString(
            """{"conversationId":"first","otherUserId":"989b8474-55bd-4e7f-9d4c-3dd54a8f6da3","status":"ACTIVE"}"""
        ).asJsonObject))
        assertEquals("알 수 없는 사용자", state.chats.single().name)
    }
}

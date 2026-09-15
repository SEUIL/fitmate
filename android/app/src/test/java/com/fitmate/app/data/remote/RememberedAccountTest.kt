package com.fitmate.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test

class RememberedAccountTest {
    private class Fixture : AutoCloseable {
        var refreshStatus = 200
        var failLogin = false
        var expireAccess = false
        var revision = 0
        val paths = CopyOnWriteArrayList<String>()
        val refreshBodies = CopyOnWriteArrayList<String>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                paths += path
                val payload = exchange.requestBody.reader().readText()
                val (status, data) = when (path) {
                    "/auth/login" -> if (failLogin) 400 to "Login failed" else 200 to mapOf("tokens" to tokens())
                    "/auth/refresh" -> {
                        refreshBodies += JsonParser.parseString(payload).asJsonObject.get("refreshToken").asString
                        if (refreshStatus == 200) { revision++; 200 to tokens() } else refreshStatus to "Refresh failed"
                    }
                    "/users/me" -> if (expireAccess) { expireAccess = false; 401 to "Expired" } else 200 to mapOf("id" to "fixture-user")
                    else -> 200 to emptyMap<String, String>()
                }
                val bytes = Gson().toJson(if (status < 400) mapOf("data" to data) else mapOf("error" to mapOf("message" to data))).toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        private fun tokens() = mapOf("accessToken" to "fixture-access-$revision", "refreshToken" to "fixture-refresh-$revision")
        val api = FitMateApiClient("http://127.0.0.1:${server.address.port}")
        fun login(remember: Boolean = true, email: String = "fixture@example.com") = api.authenticate("auth/login", emptyMap<String, String>(), email.takeIf { remember })
        override fun close() = server.stop(0)
    }

    @Test fun `remembered logout preserves account and each quick login rotates credentials`() = Fixture().use { f ->
        f.login(); f.api.logout()
        assertFalse(f.api.hasSession()); assertEquals("fixture@example.com", f.api.rememberedEmail())
        assertFalse(f.paths.contains("/auth/logout"))
        repeat(2) { f.api.continueRememberedAccount(); assertTrue(f.api.hasSession()); f.api.logout() }
        assertEquals(listOf("fixture-refresh-0", "fixture-refresh-1"), f.refreshBodies)
        assertEquals(1, f.paths.count { it == "/auth/login" })
    }

    @Test fun `normal logout revokes and clears credentials`() = Fixture().use { f ->
        f.login(remember = false); f.api.logout()
        assertEquals(listOf("/auth/login", "/users/me", "/auth/logout"), f.paths.toList())
        assertFalse(f.api.hasSession()); assertNull(f.api.rememberedEmail())
    }

    @Test fun `automatic refresh also updates remembered credential`() = Fixture().use { f ->
        f.login(); f.expireAccess = true
        f.api.request("GET", "users/me"); f.api.logout(); f.api.continueRememberedAccount()
        assertEquals(listOf("fixture-refresh-0", "fixture-refresh-1"), f.refreshBodies)
    }

    @Test fun `expired revoked and invalid credentials remove remembered account`() {
        listOf(400, 401, 403).forEach { status -> Fixture().use { f ->
            f.login(); f.api.logout(); f.refreshStatus = status
            assertEquals(401, assertThrows(ApiFailure::class.java) { f.api.continueRememberedAccount() }.status)
            assertFalse(f.api.hasSession()); assertNull(f.api.rememberedEmail())
        } }
    }

    @Test fun `temporary server failure retains account for retry`() = Fixture().use { f ->
        f.login(); f.api.logout(); f.refreshStatus = 503
        assertEquals(503, assertThrows(ApiFailure::class.java) { f.api.continueRememberedAccount() }.status)
        assertEquals("fixture@example.com", f.api.rememberedEmail()); assertFalse(f.api.hasSession())
        f.refreshStatus = 200; f.api.continueRememberedAccount()
        assertTrue(f.api.hasSession())
        assertEquals(listOf("fixture-refresh-0", "fixture-refresh-0"), f.refreshBodies)
    }

    @Test fun `forget prevents any subsequent quick login request`() = Fixture().use { f ->
        f.login(); f.api.logout(); f.api.clearSession()
        assertThrows(ApiFailure::class.java) { f.api.continueRememberedAccount() }
        assertNull(f.api.rememberedEmail()); assertTrue(f.refreshBodies.isEmpty())
    }

    @Test fun `failed login preserves previous account and successful login replaces or disables it`() = Fixture().use { f ->
        f.login(); f.api.logout(); f.failLogin = true
        assertThrows(ApiFailure::class.java) { f.login(email = "second@example.com") }
        assertEquals("fixture@example.com", f.api.rememberedEmail())
        f.failLogin = false; f.login(email = "second@example.com")
        assertEquals("second@example.com", f.api.rememberedEmail())
        f.login(remember = false); assertNull(f.api.rememberedEmail())
    }

    @Test fun `all device logout revokes remembered session too`() = Fixture().use { f ->
        f.login(); f.api.logout(all = true)
        assertTrue(f.paths.contains("/auth/logout-all"))
        assertFalse(f.api.hasSession()); assertNull(f.api.rememberedEmail())
    }
}

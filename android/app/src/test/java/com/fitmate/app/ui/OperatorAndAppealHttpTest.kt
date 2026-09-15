package com.fitmate.app.ui

import com.fitmate.app.data.remote.ApiFailure
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.UUID

class OperatorAndAppealHttpTest {
    @Test fun `limited authentication never replaces normal session and never refreshes on expiry`() {
        val seen = mutableListOf<Pair<String, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            seen += path to exchange.requestHeaders.getFirst("Authorization")
            exchange.requestBody.readBytes()
            val status = if (path == "/appeal-access/appeals") 401 else 200
            val data: Any = when (path) {
                "/auth/login" -> mapOf("tokens" to mapOf("accessToken" to "ordinary-session", "refreshToken" to "ordinary-refresh"))
                "/auth/appeal-sessions" -> mapOf("token" to "limited-session")
                else -> mapOf("items" to emptyList<Any>())
            }
            val bytes = Gson().toJson(if (status == 200) mapOf("data" to data) else mapOf("error" to mapOf("code" to "INVALID_ACCESS_TOKEN", "message" to "Expired"))).toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = FitMateApiClient("http://127.0.0.1:${server.address.port}")
            api.authenticate("auth/login", emptyMap<String, String>())
            val token = api.request("POST", "auth/appeal-sessions", emptyMap<String, String>(), authenticated = false).asJsonObject["token"].asString
            api.appealRequest("GET", "sanctions", token)
            val failure = runCatching { api.appealRequest("GET", "appeals", token) }.exceptionOrNull()
            assertEquals(401, (failure as ApiFailure).status)
            api.request("GET", "users/me")
            assertTrue(api.hasSession())
            assertEquals("Appeal limited-session", seen.single { it.first == "/appeal-access/sanctions" }.second)
            assertEquals("Bearer ordinary-session", seen.single { it.first == "/users/me" }.second)
            assertFalse(seen.any { it.first == "/auth/refresh" })
            assertTrue(runCatching { api.appealRequest("GET", "../admin/operators", token) }.isFailure)
        } finally { server.stop(0) }
    }
    @Test fun `operator command sends separate reauthentication and same key and maps expiry`() = runBlocking {
        var seenAuth: String? = null; var seenKey: String? = null; var seenReauth: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.requestBody.readBytes()
            val login = exchange.requestURI.path == "/auth/login"
            seenAuth = exchange.requestHeaders.getFirst("Authorization"); seenKey = exchange.requestHeaders.getFirst("Idempotency-Key"); seenReauth = exchange.requestHeaders.getFirst("X-Admin-Reauth")
            val bytes = Gson().toJson(if (login) mapOf("data" to mapOf("tokens" to mapOf("accessToken" to "ordinary", "refreshToken" to "refresh")))
                else mapOf("error" to mapOf("code" to "OPERATOR_REAUTH_REQUIRED", "message" to "Reauthenticate"))).toByteArray()
            exchange.sendResponseHeaders(if (login) 200 else 403, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = FitMateApiClient("http://127.0.0.1:${server.address.port}").also { it.authenticate("auth/login", emptyMap<String, String>()) }
            val key = UUID.randomUUID().toString()
            repeat(2) {
                val failure = runCatching { ServerAdminRepository(api).changeOperator(UUID.randomUUID().toString(), "ADMIN", true, "Test reason", 1, key, "extra-auth") }.exceptionOrNull()
                assertEquals(AdminError.REAUTH_REQUIRED, (failure as AdminFailure).code)
                assertEquals(key, seenKey); assertEquals("Bearer ordinary", seenAuth); assertEquals("extra-auth", seenReauth)
            }
        } finally { server.stop(0) }
    }
}

package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.UUID

class ServerAdminToolsRepositoryTest {
    private class Fixture : AutoCloseable {
        val id = UUID.randomUUID().toString()
        var path = ""
        var method = ""
        var key: String? = null
        var request = ""
        var forbidden = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                path = exchange.requestURI.path; method = exchange.requestMethod
                key = exchange.requestHeaders.getFirst("Idempotency-Key")
                request = exchange.requestBody.bufferedReader().use { it.readText() }
                val notice = mapOf("id" to id, "title" to "공지 제목", "body" to "사용자에게 보이는 본문", "status" to "PUBLISHED", "version" to 4,
                    "publishedAt" to "2026-09-15T00:00:00Z")
                val data = when {
                    path == "/auth/login" -> mapOf("tokens" to mapOf("accessToken" to "test-access", "refreshToken" to "test-refresh"))
                    path.contains("categories") -> mapOf("version" to 2, "items" to listOf(mapOf("id" to id, "code" to "HEALTH", "name" to "헬스", "active" to true)),
                        "otherSports" to listOf(mapOf("name" to "수영", "memberCount" to 3)))
                    path.endsWith("/notices") && method == "GET" -> mapOf("items" to listOf(notice), "nextCursor" to "MQ", "hasNext" to true)
                    else -> notice
                }
                val status = if (forbidden) 403 else 200
                val bytes = Gson().toJson(if (forbidden) mapOf("error" to mapOf("code" to "FORBIDDEN_RESOURCE", "message" to "Denied"))
                    else mapOf("data" to data)).toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val api = FitMateApiClient("http://127.0.0.1:${server.address.port}").also { it.authenticate("auth/login", emptyMap<String, String>()) }
        val repository = ServerAdminRepository(api)
        override fun close() = server.stop(0)
    }
    @Test fun `user notice list and detail use user endpoints`() = runBlocking {
        Fixture().use { f ->
            val page = f.repository.notices(false)
            assertEquals("/notices", f.path)
            assertEquals("MQ", page.nextCursor)
            val detail = f.repository.noticeDetail(false, page.items.first().id)
            assertEquals("/notices/${f.id}", f.path)
            assertEquals("사용자에게 보이는 본문", detail.body)
        }
    }
    @Test fun `notice retries preserve key and version and edits use PUT`() = runBlocking {
        Fixture().use { f ->
            val key = UUID.randomUUID().toString()
            repeat(2) {
                f.repository.saveNotice(f.id, "수정", "본문", 3, key)
                assertEquals("PUT", f.method); assertEquals(key, f.key)
                assertEquals(3, Gson().fromJson(f.request, JsonObject::class.java)["expectedVersion"].asLong)
            }
            f.repository.transitionNotice(f.id, "end", 4, key)
            assertEquals("/admin/notices/${f.id}/end", f.path)
        }
    }
    @Test fun `category order preserves ids and aggregate member counts`() = runBlocking {
        Fixture().use { f ->
            val catalog = f.repository.reorderCategories(listOf(f.id), 1, "test-key")
            assertEquals("/admin/categories/order", f.path)
            assertEquals("PUT", f.method)
            assertEquals(f.id, Gson().fromJson(f.request, JsonObject::class.java)["ids"].asJsonArray.first().asString)
            assertEquals(listOf("수영" to 3L), catalog.otherSports)
        }
    }
    @Test fun `revoked permissions become an explicit error instead of empty tools`() = runBlocking {
        Fixture().use { f ->
            f.forbidden = true
            try { f.repository.categories(); fail("Expected denial") }
            catch (failure: AdminFailure) { assertEquals(AdminError.FORBIDDEN, failure.code) }
        }
    }
}

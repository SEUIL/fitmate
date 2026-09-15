package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.UUID

class ServerAdminRepositoryTest {
    private class Fixture : AutoCloseable {
        var eligible = true
        var code: String? = null
        var seenKey: String? = null
        var seenQuery: String? = null
        val report = mapOf("id" to UUID.randomUUID().toString(), "targetType" to "USER", "title" to "Report",
            "reporterReason" to "SPAM", "status" to "REVIEWING", "createdAt" to "2026-09-15T00:00:00Z", "version" to 3,
            "subjectUser" to mapOf("id" to UUID.randomUUID().toString(), "nickname" to "Member"))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                var status = 200
                val data: Any = when {
                    path == "/auth/login" -> mapOf("tokens" to mapOf("accessToken" to "fixture-access", "refreshToken" to "fixture-refresh"))
                    path == "/admin/access" -> mapOf("eligible" to eligible)
                    path == "/admin/dashboard" -> mapOf("pendingReports" to mapOf("value" to 123), "newUsers" to mapOf("value" to 7),
                        "completedWorkouts" to mapOf("value" to 9), "failedAnalyses" to mapOf("value" to 0), "recentReports" to listOf(report), "recentHistory" to emptyList<Any>())
                    path == "/admin/reports" -> { seenQuery = exchange.requestURI.rawQuery; mapOf("items" to listOf(report), "nextCursor" to "MQ", "hasNext" to true) }
                    path == "/admin/members" || path == "/admin/contents" -> mapOf("items" to emptyList<Any>())
                    else -> { seenKey = exchange.requestHeaders.getFirst("Idempotency-Key"); if (code != null) status = 409
                        mapOf("report" to report, "evidence" to mapOf("availability" to "UNAVAILABLE", "reporterStatement" to "Statement"),
                            "history" to emptyList<Any>(), "allowedActions" to listOf("NONE"),
                            "decision" to mapOf("action" to "WARNING", "publicReason" to "Reason", "internalNote" to "Private note")) }
                }
                exchange.requestBody.close()
                val bytes = Gson().toJson(if (status == 200) mapOf("data" to data) else mapOf("error" to mapOf("code" to code, "message" to "Conflict"))).toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val api = FitMateApiClient("http://127.0.0.1:${server.address.port}").also { it.authenticate("auth/login", emptyMap<String, String>()) }
        val repo = ServerAdminRepository(api)
        override fun close() = server.stop(0)
    }
    @Test fun `server counts cursor evidence and allowed actions survive mapping`() = runBlocking {
        Fixture().use { f ->
            val page = f.repo.search("신고 검색", AdminReportStatus.REVIEWING, "MQ")
            assertEquals(123, page.pendingCount)
            assertEquals("MQ", page.nextCursor)
            assertTrue(f.seenQuery!!.contains("status=REVIEWING"))
            assertTrue(f.seenQuery!!.contains("cursor=MQ"))
            val detail = f.repo.detail(page.reports.first().id)
            assertNull(detail.evidence)
            assertEquals("Statement", detail.reporterStatement)
            assertEquals(listOf(AdminAction.NONE), detail.allowedActions)
            assertTrue(detail.decision!!.contains("Private note"))
        }
    }
    @Test fun `permission denial does not load fixtures`() = runBlocking {
        Fixture().use { f ->
            f.eligible = false
            val state = AdminUiState(f.repo)
            assertFalse(state.load()); assertNull(state.snapshot); assertTrue(state.accessBlocked)
        }
    }
    @Test fun `decision preserves idempotency key and maps conflict code`() = runBlocking {
        Fixture().use { f ->
            f.code = "IDEMPOTENCY_KEY_CONFLICT"
            val key = UUID.randomUUID().toString()
            try {
                f.repo.submit(AdminDecisionCommand(f.report["id"].toString(), 3, AdminAction.WARNING, AdminDecisionReason.SPAM, "Reason", null, null, key))
                fail("Expected conflict")
            } catch (failure: AdminFailure) { assertEquals(AdminError.IDEMPOTENCY_CONFLICT, failure.code) }
            assertEquals(key, f.seenKey)
        }
    }
}

package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in: a disposable server/database and a pre-granted test operator are required. */
class ServerAdminLiveTest {
    @Test fun `Android repository completes real server moderation round trip`() = runBlocking {
        val url = System.getenv("FITMATE_ADMIN_TEST_URL")
        assumeTrue(!url.isNullOrBlank())
        val operator = FitMateApiClient(requireNotNull(url))
        operator.authenticate("auth/login", mapOf("email" to requireNotNull(System.getenv("FITMATE_ADMIN_TEST_EMAIL")),
            "password" to requireNotNull(System.getenv("FITMATE_ADMIN_TEST_PASSWORD"))))
        val reporter = FitMateApiClient(url)
        reporter.authenticate("auth/signup", signup())
        val subject = FitMateApiClient(url)
        subject.authenticate("auth/signup", signup())
        val subjectId = subject.request("GET", "users/me").asJsonObject["id"].asString
        val created = reporter.request("POST", "reports", mapOf("targetType" to "USER", "targetId" to subjectId,
            "reason" to "SPAM", "detail" to "Live integration statement")).asJsonObject["reportId"].asString
        val denied = AdminUiState(ServerAdminRepository(reporter))
        assertFalse(denied.load()); assertTrue(denied.accessBlocked)
        val repo = ServerAdminRepository(operator)
        val page = repo.search(created, AdminReportStatus.RECEIVED, null)
        assertEquals(created, page.reports.single().id)
        assertTrue(page.remote)
        val initial = repo.detail(created)
        assertEquals("Live integration statement", initial.reporterStatement)
        repo.claim(created, initial.version)
        val claimed = repo.detail(created)
        assertEquals(AdminReportStatus.REVIEWING, claimed.status)
        assertTrue(claimed.allowedActions.contains(AdminAction.WARNING))
        val command = AdminDecisionCommand(created, claimed.version, AdminAction.WARNING, AdminDecisionReason.SPAM,
            "Integration warning", null, "Integration internal note", UUID.randomUUID().toString())
        repo.submit(command)
        repo.submit(command)
        val resolved = repo.detail(created)
        assertEquals(AdminReportStatus.RESOLVED, resolved.status)
        assertTrue(resolved.decision!!.contains("Integration warning"))
        assertTrue(resolved.decision.contains("Integration internal note"))
        assertTrue(resolved.allowedActions.isEmpty())
    }
    private fun signup() = mapOf("email" to "android-admin-${UUID.randomUUID()}@example.com",
        "password" to UUID.randomUUID().toString(), "nickname" to "android-${UUID.randomUUID().toString().take(16)}",
        "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
        "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
        "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"))
}

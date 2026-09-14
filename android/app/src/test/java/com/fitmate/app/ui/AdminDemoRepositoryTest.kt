package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AdminDemoRepositoryTest {
    @Test fun reviewMustStartBeforeDecision() = runBlocking {
        val repo = FakeAdminRepository(latencyMs = 0)
        val draft = AdminDecisionCommand("R-1001", 0, AdminAction.NONE, AdminDecisionReason.NO_VIOLATION, "위반 없음", null, null, "first")
        expectFailure(AdminError.ALREADY_PROCESSED) { repo.submit(draft) }
        repo.claim("R-1001", 0)
        repo.submit(draft.copy(expectedVersion = 1))
        assertEquals(AdminReportStatus.DISMISSED, repo.reports.first().status)
        expectFailure(AdminError.ALREADY_PROCESSED) { repo.submit(draft.copy(expectedVersion = 2, idempotencyKey = "new")) }
    }
    @Test fun invalidSanctionsCannotBeSaved() = runBlocking {
        val repo = FakeAdminRepository(latencyMs = 0)
        repo.claim("R-1001", 0)
        val draft = AdminDecisionCommand("R-1001", 1, AdminAction.SUSPEND, AdminDecisionReason.SPAM, "반복적인 광고 게시", 7, null, "first")
        expectFailure(AdminError.VALIDATION) { repo.submit(draft.copy(durationDays = 2)) }
        expectFailure(AdminError.VALIDATION) { repo.submit(draft.copy(publicReason = "  ")) }
        assertFalse(AdminAction.entries.any { it.name == "BAN" })
        expectFailure(AdminError.VALIDATION) { repo.submit(draft.copy(action = AdminAction.WARNING)) }
        expectFailure(AdminError.VALIDATION) { repo.submit(draft.copy(reasonCode = AdminDecisionReason.NO_VIOLATION)) }
        repo.submit(draft)
        assertEquals(1, repo.history.size)
        assertEquals(AdminReportStatus.RESOLVED, repo.reports.first().status)
    }
    @Test fun filtersAndCountsReflectDecisions() = runBlocking {
        val repo = FakeAdminRepository(latencyMs = 0)
        assertEquals(5, repo.fetch().pendingCount)
        assertEquals(1, repo.fetch().search("러너01", null).size)
        repo.claim("R-1001", 0)
        val updated = repo.submit(AdminDecisionCommand("R-1001", 1, AdminAction.NONE, AdminDecisionReason.NO_VIOLATION, "위반 없음", null, null, "first"))
        assertEquals(4, updated.pendingCount)
        assertEquals(1, updated.search("", AdminReportStatus.DISMISSED).size)
    }

    private suspend fun expectFailure(expected: AdminError, action: suspend () -> Unit) {
        try { action(); fail("Expected $expected") } catch (error: AdminFailure) { assertEquals(expected, error.code) }
    }
}

package com.fitmate.app.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdminIntegrationStateTest {
    @Test fun listRefreshCannotUnlockAnUncertainDecision() = runBlocking {
        val fake = FakeAdminRepository(latencyMs = 0)
        val state = AdminUiState(object : AdminRepository by fake {
            override suspend fun claim(id: String, version: Long): AdminSnapshot = throw AdminFailure(AdminError.NETWORK)
        })
        state.load()
        state.open("R-1001")
        state.claim(requireNotNull(state.selected))
        assertTrue(state.requiresRefresh)
        state.search("", null)
        assertTrue(state.requiresRefresh)
        state.load()
        assertFalse(state.requiresRefresh)
    }

    @Test fun structuredDecisionIsIdempotentAndRejectsChangedPayload() = runBlocking {
        val repo = FakeAdminRepository(latencyMs = 0)
        repo.claim("R-1001", 0)
        val command = AdminDecisionCommand("R-1001", 1, AdminAction.WARNING, AdminDecisionReason.POLICY_VIOLATION, "운영 기준 위반 안내", null, null, "test-key")
        val first = repo.submit(command)
        assertEquals(first, repo.submit(command))
        assertEquals(1, first.history.size)
        try { repo.submit(command.copy(publicReason = "다른 안내")); fail("Changed payload must conflict") }
        catch (error: AdminFailure) { assertEquals(AdminError.IDEMPOTENCY_CONFLICT, error.code) }
    }

    @Test fun staleVersionCannotChangeReport() = runBlocking {
        val repo = FakeAdminRepository(latencyMs = 0)
        repo.claim("R-1001", 0)
        try { repo.claim("R-1001", 0); fail("Stale version must conflict") }
        catch (error: AdminFailure) { assertEquals(AdminError.VERSION_CONFLICT, error.code) }
        assertEquals(AdminReportStatus.REVIEWING, repo.fetch().reports.first().status)
    }

    @Test fun failedReadIsNotShownAsZeroAndCanRetry() = runBlocking {
        var failRead = true
        val fake = FakeAdminRepository(latencyMs = 0)
        val repo = object : AdminRepository by fake {
            override suspend fun fetch(): AdminSnapshot {
                if (failRead) throw AdminFailure(AdminError.NETWORK)
                return fake.fetch()
            }
        }
        val state = AdminUiState(repo)
        assertNull(state.snapshot)
        state.load()
        assertNull(state.snapshot)
        assertEquals(AdminError.NETWORK, state.error)
        failRead = false
        state.load()
        assertEquals(5, state.snapshot?.pendingCount)
        assertNull(state.error)
    }

    @Test fun mutationFailureRequiresRefreshBeforeFurtherActions() = runBlocking {
        val fake = FakeAdminRepository(latencyMs = 0)
        val repo = object : AdminRepository by fake {
            override suspend fun claim(id: String, version: Long): AdminSnapshot = throw AdminFailure(AdminError.VERSION_CONFLICT)
        }
        val state = AdminUiState(repo)
        state.load()
        assertFalse(state.claim(state.snapshot!!.reports.first()))
        assertTrue(state.requiresRefresh)
        state.load()
        assertFalse(state.requiresRefresh)
    }

    @Test fun doubleSubmissionIsBlockedWhileBusy() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val fake = FakeAdminRepository(latencyMs = 0)
        var calls = 0
        val repo = object : AdminRepository by fake {
            override suspend fun claim(id: String, version: Long): AdminSnapshot {
                calls++
                release.await()
                return fake.claim(id, version)
            }
        }
        val state = AdminUiState(repo)
        state.load()
        val report = state.snapshot!!.reports.first()
        val first = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { state.claim(report) }
        assertFalse(state.claim(report))
        assertEquals(1, calls)
        release.complete(Unit)
        first.join()
        assertFalse(state.busy)
    }

    @Test fun cancellationDoesNotBecomeAnError() = runBlocking {
        val repo = object : AdminRepository by FakeAdminRepository(latencyMs = 0) {
            override suspend fun fetch(): AdminSnapshot = throw kotlinx.coroutines.CancellationException()
        }
        val state = AdminUiState(repo)
        try { state.load(); fail("Cancellation must propagate") } catch (_: kotlinx.coroutines.CancellationException) { }
        assertNull(state.error)
        assertFalse(state.busy)
    }
}

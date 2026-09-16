package com.fitmate.app.samsunghealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExternalWorkoutLookupTest {
    private val start = Instant.parse("2026-09-10T01:00:00Z")
    private val source = workout("source", "app")

    @Test fun `found and confirmed missing are distinct and missing never deletes automatically`() {
        val found = classifyExternalWorkoutLookup(
            state(SamsungHealthStatus.READY, listOf(source)), source.uid, source.sourceAppId,
        )
        val missing = classifyExternalWorkoutLookup(
            state(SamsungHealthStatus.DATA_EMPTY), source.uid, source.sourceAppId,
        )

        assertEquals(ExternalWorkoutLookupState.FOUND, found.state)
        assertSame(source, found.workout)
        assertEquals(ExternalWorkoutLookupState.NOT_FOUND, missing.state)
        assertFalse(missing.allowsExplicitUnlink(userConfirmed = false))
        assertTrue(missing.allowsExplicitUnlink(userConfirmed = true))
    }

    @Test fun `permission denial preserves stored record and cannot call delete`() {
        val result = classifyExternalWorkoutLookup(
            state(SamsungHealthStatus.PERMISSION_MISSING), source.uid, source.sourceAppId,
        )
        val authorization = classifyExternalWorkoutLookup(
            state(SamsungHealthStatus.AUTHORIZATION_ERROR), source.uid, source.sourceAppId,
        )

        assertEquals(ExternalWorkoutLookupState.PERMISSION_ERROR, result.state)
        assertEquals(ExternalWorkoutLookupState.PERMISSION_ERROR, authorization.state)
        assertEquals(0, simulatedDeleteCalls(result, userConfirmed = true))
        assertEquals(0, simulatedDeleteCalls(authorization, userConfirmed = true))
    }

    @Test fun `platform error preserves stored record and cannot call delete`() {
        val result = classifyExternalWorkoutLookup(
            state(SamsungHealthStatus.PLATFORM_ERROR), source.uid, source.sourceAppId,
        )

        assertEquals(ExternalWorkoutLookupState.PLATFORM_ERROR, result.state)
        assertEquals(0, simulatedDeleteCalls(result, userConfirmed = true))
    }

    @Test fun `temporary failure preserves stored record and cannot call delete`() {
        val result = classifyExternalWorkoutLookup(
            state(SamsungHealthStatus.UNKNOWN_ERROR), source.uid, source.sourceAppId,
        )

        assertEquals(ExternalWorkoutLookupState.TEMPORARY_FAILURE, result.state)
        assertEquals(0, simulatedDeleteCalls(result, userConfirmed = true))
    }

    @Test fun `modified source requires reconfirmation and cancellation keeps previous snapshot`() {
        val previous = source.toExternalWorkoutSummary()
        val modified = source.copy(sourceModifiedAt = start.plusSeconds(10), activeDurationSeconds = 1_700)
        val lookup = classifyExternalWorkoutLookup(
            state(SamsungHealthStatus.READY, listOf(modified)), source.uid, source.sourceAppId,
        )

        assertEquals(ExternalWorkoutLookupState.FOUND, lookup.state)
        assertEquals(ExternalWorkoutRecheck.MODIFIED, recheckExternalWorkoutSnapshot(source, lookup.workout))
        assertEquals(previous, source.toExternalWorkoutSummary())
        assertEquals(0, simulatedDeleteCalls(lookup, userConfirmed = true))
    }

    private fun simulatedDeleteCalls(result: ExternalWorkoutLookupResult, userConfirmed: Boolean): Int {
        var calls = 0
        if (result.allowsExplicitUnlink(userConfirmed)) calls++
        return calls
    }

    private fun state(
        status: SamsungHealthStatus,
        workouts: List<SamsungWorkoutUiModel> = emptyList(),
    ) = SamsungHealthUiState(status = status, workouts = workouts)

    private fun workout(uid: String, app: String?) = SamsungWorkoutUiModel(
        uid = uid,
        sourceAppId = app,
        sourceModifiedAt = start,
        device = null,
        exerciseType = "RUNNING",
        customTitle = null,
        startTime = start,
        endTime = start.plusSeconds(1_800),
        activeDurationSeconds = 1_800,
        sessionCount = 1,
        distanceMeters = null,
        calories = null,
        meanHeartRate = null,
        minHeartRate = null,
        maxHeartRate = null,
        meanSpeedMetersPerSecond = null,
        maxSpeedMetersPerSecond = null,
        altitudeGainMeters = null,
        altitudeLossMeters = null,
        meanCadence = null,
        vo2Max = null,
        logSampleCount = 0,
    )
}

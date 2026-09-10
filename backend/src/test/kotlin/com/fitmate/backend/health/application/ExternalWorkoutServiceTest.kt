package com.fitmate.backend.health.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.health.api.PutExternalWorkoutRequest
import com.fitmate.backend.health.domain.ExternalWorkoutSummary
import com.fitmate.backend.health.repository.ExternalWorkoutSummaryRepository
import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ExternalWorkoutServiceTest {
    private val sessionRepository = mock(WorkoutSessionRepository::class.java)
    private val summaryRepository = mock(ExternalWorkoutSummaryRepository::class.java)
    private val service = ExternalWorkoutService(sessionRepository, summaryRepository)
    private val userId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val storedSessionId = UuidBinaryConverter.toMysqlOrderedUuid(sessionId)
    private val startedAt = Instant.parse("2026-09-10T01:00:00Z")
    private val endedAt = startedAt.plusSeconds(1_800)

    @Test
    fun `creates normalized minimal summary with initial version zero`() {
        completedSession()
        `when`(summaryRepository.findOwnedForUpdate(storedSessionId, userId)).thenReturn(null)
        `when`(summaryRepository.findByUserIdAndProviderAndSourceAppIdAndSourceRecordId(userId, ExternalWorkoutSummary.PROVIDER, null, "UID-A"))
            .thenReturn(null)
        `when`(summaryRepository.saveAndFlush(any(ExternalWorkoutSummary::class.java))).thenAnswer { it.arguments[0] }

        val result = service.put(userId, sessionId, request(calories = BigDecimal("123.455")))

        assertEquals(sessionId, result.sessionId)
        assertEquals(BigDecimal("123.46"), result.caloriesKcal)
        assertNull(result.distanceMeters)
        assertEquals(0, result.version)
        verify(summaryRepository).saveAndFlush(any(ExternalWorkoutSummary::class.java))
    }

    @Test
    fun `rejects unsupported type and time mismatch`() {
        completedSession()
        val typeError = assertThrows(ApiException::class.java) { service.put(userId, sessionId, request().copy(exerciseType = "OTHER")) }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, typeError.errorCode)
        val timeError = assertThrows(ApiException::class.java) {
            service.put(userId, sessionId, request().copy(startedAt = startedAt.minusSeconds(601), endedAt = endedAt))
        }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, timeError.errorCode)
    }

    @Test
    fun `same payload retry succeeds without changing version`() {
        completedSession()
        val hash = captureCreatedHash()
        val entity = ExternalWorkoutSummary(
            workoutSessionId = storedSessionId, userId = userId, sourceRecordId = "UID-A", sourceAppId = null,
            sourceModifiedAt = startedAt, exerciseType = "BENCH_PRESS", startedAt = startedAt, endedAt = endedAt,
            activeDurationSeconds = 1_700, caloriesKcal = BigDecimal("123.46"), payloadHash = hash,
        )
        `when`(summaryRepository.findOwnedForUpdate(storedSessionId, userId)).thenReturn(entity)
        val first = service.put(userId, sessionId, request(calories = BigDecimal("123.455")))
        val second = service.put(userId, sessionId, request(calories = BigDecimal("123.455")))
        assertEquals(first.version, second.version)
    }

    @Test
    fun `older source modification cannot overwrite current version`() {
        completedSession()
        val entity = ExternalWorkoutSummary(
            workoutSessionId = storedSessionId, userId = userId, sourceRecordId = "UID-A",
            sourceModifiedAt = startedAt.plusSeconds(10), exerciseType = "BENCH_PRESS",
            startedAt = startedAt, endedAt = endedAt, activeDurationSeconds = 1_700,
            caloriesKcal = BigDecimal("100.00"), payloadHash = "different", version = 2,
        )
        `when`(summaryRepository.findOwnedForUpdate(storedSessionId, userId)).thenReturn(entity)
        val failure = assertThrows(ApiException::class.java) {
            service.put(userId, sessionId, request(calories = BigDecimal("101")).copy(
                sourceModifiedAt = startedAt, expectedVersion = 2,
            ))
        }
        assertEquals(ApiErrorCode.EXTERNAL_WORKOUT_CONFLICT, failure.errorCode)
    }

    private fun captureCreatedHash(): String {
        `when`(summaryRepository.findOwnedForUpdate(storedSessionId, userId)).thenReturn(null)
        `when`(summaryRepository.findByUserIdAndProviderAndSourceAppIdAndSourceRecordId(userId, ExternalWorkoutSummary.PROVIDER, null, "UID-A"))
            .thenReturn(null)
        var hash = ""
        `when`(summaryRepository.saveAndFlush(any(ExternalWorkoutSummary::class.java))).thenAnswer {
            (it.arguments[0] as ExternalWorkoutSummary).also { saved -> hash = saved.payloadHash }
        }
        service.put(userId, sessionId, request(calories = BigDecimal("123.455")))
        return hash
    }

    private fun completedSession() {
        `when`(sessionRepository.findByIdAndUserIdForUpdate(storedSessionId, userId)).thenReturn(
            WorkoutSession(
                id = storedSessionId, userId = userId, sportCode = SportCode.HEALTH,
                status = WorkoutSessionStatus.COMPLETED, startedAt = startedAt, endedAt = endedAt,
            ),
        )
    }

    private fun request(calories: BigDecimal? = BigDecimal("123.455")) = PutExternalWorkoutRequest(
        provider = ExternalWorkoutSummary.PROVIDER, sourceRecordId = "UID-A", sourceAppId = null,
        sourceModifiedAt = startedAt, exerciseType = "BENCH_PRESS", startedAt = startedAt,
        endedAt = endedAt, activeDurationSeconds = 1_700, caloriesKcal = calories,
    )
}

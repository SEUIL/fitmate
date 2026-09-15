package com.fitmate.backend.sportrecord.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.sportrecord.api.CreateSportRecordRequest
import com.fitmate.backend.sportrecord.domain.SportRecord
import com.fitmate.backend.sportrecord.repository.SportRecordRepository
import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class SportRecordServiceTest {
    private val sessions = mock(WorkoutSessionRepository::class.java)
    private val records = mock(SportRecordRepository::class.java)
    private val persistence = mock(SportRecordPersistenceService::class.java)
    private val service = SportRecordService(sessions, records, persistence)
    private val userId = UUID.randomUUID()
    private val recordId = UUID.randomUUID()
    private val startedAt = Instant.parse("2026-09-14T05:00:00Z")

    @Test fun `creates badminton record with normalized optional strings`() {
        `when`(sessions.findById(anyValue())).thenReturn(Optional.empty())
        `when`(persistence.save(anyValue(), anyValue())).thenAnswer { it.arguments[1] }
        val result = service.create(userId, request(placeName = "  체육관  "))
        assertEquals(recordId, result.response.id)
        assertEquals("체육관", result.response.placeName)
        assertEquals(120, result.response.elapsedDurationSeconds)
    }

    @Test fun `same normalized request retries without creating another row`() {
        val storedId = UuidBinaryConverter.toMysqlOrderedUuid(recordId)
        val session = WorkoutSession(id = storedId, userId = userId, sportCode = SportCode.BADMINTON, startedAt = startedAt, endedAt = startedAt.plusSeconds(120))
        val record = SportRecord(workoutSessionId = storedId, workoutSession = session, activityType = "DOUBLES", activeDurationSeconds = 90, placeName = "체육관", gameCount = 3, winCount = 2)
        `when`(sessions.findById(storedId)).thenReturn(Optional.of(session))
        `when`(records.findById(storedId)).thenReturn(Optional.of(record))
        assertFalse(service.create(userId, request(placeName = " 체육관 ")).created)
    }

    @Test fun `rejects cross sport result by field even when its value equals an allowed field`() {
        val crossSport = assertThrows(ApiException::class.java) { service.create(userId, request().copy(distanceMeters = 10)) }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, crossSport.errorCode)
        assertEquals("NOT_ALLOWED", crossSport.fields.single().reason)
        val sameValue = assertThrows(ApiException::class.java) { service.create(userId, request().copy(attemptCount = 3)) }
        assertEquals("attemptCount", sameValue.fields.single().field)
        assertEquals("NOT_ALLOWED", sameValue.fields.single().reason)
    }

    @Test fun `rejects inconsistent winner count`() {
        val winners = assertThrows(ApiException::class.java) { service.create(userId, request().copy(winCount = 4)) }
        assertEquals("INCONSISTENT_VALUE", winners.fields.single().reason)
    }

    @Test fun `concurrent conflict hides another users record`() {
        val storedId = UuidBinaryConverter.toMysqlOrderedUuid(recordId)
        `when`(sessions.findById(storedId)).thenReturn(Optional.empty(), Optional.of(WorkoutSession(id = storedId, userId = UUID.randomUUID())))
        `when`(persistence.save(anyValue(), anyValue())).thenThrow(DataIntegrityViolationException("duplicate"))

        val error = assertThrows(ApiException::class.java) { service.create(userId, request()) }

        assertEquals(ApiErrorCode.RESOURCE_NOT_FOUND, error.errorCode)
    }

    @Test fun `concurrent conflict with owners other record is classified as conflict`() {
        val storedId = UuidBinaryConverter.toMysqlOrderedUuid(recordId)
        `when`(sessions.findById(storedId)).thenReturn(Optional.empty(), Optional.of(WorkoutSession(id = storedId, userId = userId)))
        `when`(persistence.save(anyValue(), anyValue())).thenThrow(DataIntegrityViolationException("duplicate"))
        `when`(records.findById(storedId)).thenReturn(Optional.empty())

        val error = assertThrows(ApiException::class.java) { service.create(userId, request()) }

        assertEquals(ApiErrorCode.SPORT_RECORD_CONFLICT, error.errorCode)
    }

    private fun request(placeName: String? = "체육관") = CreateSportRecordRequest(
        clientRecordId = recordId, sportCode = SportCode.BADMINTON, activityType = "DOUBLES", startedAt = startedAt,
        completedAt = startedAt.plusSeconds(120), activeDurationSeconds = 90, placeName = placeName, gameCount = 3, winCount = 2,
    )
    @Suppress("UNCHECKED_CAST") private fun <T> anyValue(): T = org.mockito.ArgumentMatchers.any<T>()
}

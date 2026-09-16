package com.fitmate.backend.recommendation.application

import com.fitmate.backend.running.domain.RunType
import com.fitmate.backend.running.domain.RunningRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorkoutPlanContextReaderTest {
    @Test
    fun `pace target is achieved when actual pace is faster`() {
        assertEquals(true, recommendationGoalAchieved(paceRun(actualPace = 330)))
    }

    @Test
    fun `pace target is achieved when actual pace equals target`() {
        assertEquals(true, recommendationGoalAchieved(paceRun(actualPace = 360)))
    }

    @Test
    fun `pace target is not achieved when actual pace is slower`() {
        assertEquals(false, recommendationGoalAchieved(paceRun(actualPace = 390)))
    }

    @Test
    fun `pace target is unknown when actual pace is absent`() {
        assertNull(recommendationGoalAchieved(paceRun(actualPace = null)))
    }

    @Test
    fun `pace target is unknown when target pace is absent`() {
        assertNull(recommendationGoalAchieved(paceRun(actualPace = 330).apply { targetPaceSecondsPerKm = null }))
    }

    @Test
    fun `pace target is unknown for invalid empty record and false for valid short record`() {
        assertNull(recommendationGoalAchieved(paceRun(actualPace = 330).apply {
            totalDistanceMeters = 0
            activeDurationSeconds = 0
        }))
        assertEquals(false, recommendationGoalAchieved(paceRun(actualPace = 330).apply {
            totalDistanceMeters = 1_000
            activeDurationSeconds = 330
        }))
    }

    @Test
    fun `pace target is unknown when target distance is absent`() {
        assertNull(recommendationGoalAchieved(paceRun(actualPace = 330).apply { targetDistanceMeters = null }))
    }

    @Test
    fun `distance time interval and free goal behavior is unchanged`() {
        assertEquals(true, recommendationGoalAchieved(RunningRecord(
            runType = RunType.DISTANCE_TARGET,
            targetDistanceMeters = 5_000,
            totalDistanceMeters = 5_000,
        )))
        assertEquals(false, recommendationGoalAchieved(RunningRecord(
            runType = RunType.TIME_TARGET,
            targetDurationSeconds = 1_800,
            activeDurationSeconds = 1_799,
        )))
        assertEquals(true, recommendationGoalAchieved(RunningRecord(
            runType = RunType.INTERVAL,
            targetDurationSeconds = 600,
            activeDurationSeconds = 600,
        )))
        assertNull(recommendationGoalAchieved(RunningRecord(runType = RunType.FREE)))
    }

    private fun paceRun(actualPace: Int?) = RunningRecord(
        runType = RunType.PACE_TARGET,
        targetDistanceMeters = 5_000,
        targetPaceSecondsPerKm = 360,
        totalDistanceMeters = 5_000,
        activeDurationSeconds = 1_800,
        averagePaceSecondsPerKm = actualPace,
    )
}

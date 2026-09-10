package com.fitmate.app.samsunghealth

import java.time.Duration
import java.time.Instant

object WorkoutMatchEngine {
    private val allowedBySport = mapOf(
        "HEALTH" to setOf("BENCH_PRESS"),
        "RUNNING" to setOf("RUNNING"),
    )
    private val tolerance = Duration.ofMinutes(10)

    fun candidates(sportCode: String, sessionStartedAt: Instant, sessionEndedAt: Instant, workouts: List<SamsungWorkoutUiModel>): List<SamsungWorkoutUiModel> =
        workouts.asSequence()
            .filter { it.sessionCount == 1 }
            .filter { it.activeDurationSeconds > 0 }
            .filter { it.exerciseType in allowedBySport[sportCode].orEmpty() }
            .filter { Duration.between(sessionStartedAt, it.startTime).abs() <= tolerance }
            .filter { Duration.between(sessionEndedAt, it.endTime).abs() <= tolerance }
            .filter { it.startTime.isBefore(sessionEndedAt) && sessionStartedAt.isBefore(it.endTime) }
            .distinctBy { SourceKey(it.sourceAppId ?: "", it.uid) }
            .sortedWith(compareBy<SamsungWorkoutUiModel>(
                { Duration.between(sessionStartedAt, it.startTime).abs().toMillis() + Duration.between(sessionEndedAt, it.endTime).abs().toMillis() },
                { it.sourceAppId ?: "" }, { it.uid },
            ))
            .toList()

    fun isSupported(sportCode: String, exerciseType: String) = exerciseType in allowedBySport[sportCode].orEmpty()
}

private data class SourceKey(val sourceAppKey: String, val sourceRecordId: String)

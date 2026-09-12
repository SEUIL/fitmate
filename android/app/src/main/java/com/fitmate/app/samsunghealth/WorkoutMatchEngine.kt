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
        analyze(sportCode, sessionStartedAt, sessionEndedAt, workouts).candidates

    fun analyze(
        sportCode: String,
        sessionStartedAt: Instant,
        sessionEndedAt: Instant,
        workouts: List<SamsungWorkoutUiModel>,
        sessionExerciseNames: List<String> = emptyList(),
    ): WorkoutMatchAnalysis {
        val unique = workouts.distinctBy { SourceKey(it.sourceAppId ?: "", it.uid) }
        val compound = unique.filter { it.sessionCount != 1 }
        val incomplete = unique.filter { it.sessionCount == 1 && it.activeDurationSeconds <= 0 }
        val eligibleShape = unique.filter { it.sessionCount == 1 && it.activeDurationSeconds > 0 }
        val unsupported = eligibleShape.filterNot { isSupported(sportCode, it.exerciseType) }.map { workout ->
            UnsupportedWorkout(
                workout = workout,
                matchingSessionExerciseNames = matchingExerciseNames(workout.customTitle, sessionExerciseNames),
            )
        }
        val supported = eligibleShape.filter { isSupported(sportCode, it.exerciseType) }
        val candidates = supported.filter { matchesTime(sessionStartedAt, sessionEndedAt, it) }
            .sortedWith(compareBy<SamsungWorkoutUiModel>(
                { Duration.between(sessionStartedAt, it.startTime).abs().toMillis() + Duration.between(sessionEndedAt, it.endTime).abs().toMillis() },
                { it.sourceAppId ?: "" }, { it.uid },
            ))
        return WorkoutMatchAnalysis(
            candidates = candidates,
            unsupported = unsupported,
            compoundCount = compound.size,
            timeMismatchCount = supported.count { !matchesTime(sessionStartedAt, sessionEndedAt, it) },
            incompleteCount = incomplete.size,
        )
    }

    fun isSupported(sportCode: String, exerciseType: String) = exerciseType in allowedBySport[sportCode].orEmpty()

    fun matchingExerciseNames(customTitle: String?, sessionExerciseNames: List<String>): List<String> {
        val normalizedTitle = customTitle?.let(::normalizeName)?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return sessionExerciseNames.filter { normalizeName(it) == normalizedTitle }.distinct()
    }

    private fun matchesTime(sessionStartedAt: Instant, sessionEndedAt: Instant, workout: SamsungWorkoutUiModel) =
        Duration.between(sessionStartedAt, workout.startTime).abs() <= tolerance &&
            Duration.between(sessionEndedAt, workout.endTime).abs() <= tolerance &&
            workout.startTime.isBefore(sessionEndedAt) && sessionStartedAt.isBefore(workout.endTime)

    private fun normalizeName(value: String) = value.trim().lowercase().filterNot(Char::isWhitespace)
}

data class WorkoutMatchAnalysis(
    val candidates: List<SamsungWorkoutUiModel>,
    val unsupported: List<UnsupportedWorkout>,
    val compoundCount: Int,
    val timeMismatchCount: Int,
    val incompleteCount: Int,
)

data class UnsupportedWorkout(
    val workout: SamsungWorkoutUiModel,
    val matchingSessionExerciseNames: List<String>,
)

private data class SourceKey(val sourceAppKey: String, val sourceRecordId: String)

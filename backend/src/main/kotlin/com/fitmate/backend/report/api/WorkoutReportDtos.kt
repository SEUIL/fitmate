package com.fitmate.backend.report.api

import com.fitmate.backend.fitness.domain.BodyPart
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class WorkoutReportResponse(
    val workoutSessionId: UUID,
    val reportStatus: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val durationSeconds: Long,
    val exerciseCount: Int,
    val completedSetCount: Int,
    val totalRepetitions: Int,
    val totalVolumeKg: BigDecimal,
    val exercises: List<WorkoutExerciseReportResponse>,
)

data class WorkoutExerciseReportResponse(
    val exerciseId: UUID,
    val exerciseName: String,
    val bodyPart: BodyPart?,
    val completedSetCount: Int,
    val totalRepetitions: Int,
    val totalVolumeKg: BigDecimal,
    val maxWeightKg: BigDecimal?,
    val previousComparison: PreviousWorkoutComparisonResponse?,
)

data class PreviousWorkoutComparisonResponse(
    val previousWorkoutSessionId: UUID,
    val previousMaxWeightKg: BigDecimal?,
    val currentMaxWeightKg: BigDecimal?,
    val previousVolumeKg: BigDecimal,
    val currentVolumeKg: BigDecimal,
    val volumeChangeKg: BigDecimal,
)

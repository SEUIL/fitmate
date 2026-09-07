package com.fitmate.backend.workout.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateWorkoutSessionRequest(
    val routineId: UUID? = null,
    @field:Valid @field:Size(min = 1, max = 100) val exercises: List<WorkoutExercisePlanRequest>? = null,
    val clientUpdatedAt: Instant = Instant.now(),
)

data class WorkoutExercisePlanRequest(
    val exerciseId: UUID,
    @field:Min(1) @field:Max(100) val sequenceNo: Int,
    @field:Min(1) @field:Max(100) val targetSets: Int,
    @field:DecimalMin("0.0") val targetWeightKg: BigDecimal? = null,
    @field:Min(1) @field:Max(10000) val targetRepetitions: Int? = null,
)

data class WorkoutStateRequest(
    @param:JsonProperty(required = true) @param:JsonSetter(nulls = Nulls.FAIL) @field:Min(0) val version: Long,
    val clientUpdatedAt: Instant = Instant.now(),
    @field:Size(max = 255) val cancelReason: String? = null,
)

data class UpsertWorkoutSetRequest(
    val weightKg: BigDecimal? = null,
    val repetitions: Int? = null,
    val durationSeconds: Int? = null,
    val rpe: Int? = null,
    val completed: Boolean = true,
    @field:Size(max = 500) val memo: String? = null,
    val clientUpdatedAt: Instant = Instant.now(),
    @field:Min(0) val version: Long? = null,
)

data class WorkoutSessionResponse(
    val id: UUID,
    val routineId: UUID?,
    val routineSplitDayNo: Int?,
    val routineSplitCount: Int?,
    val routineWorkoutDayNumber: Int?,
    val focusBodyParts: List<BodyPart>,
    val status: WorkoutSessionStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val version: Long,
    val exercises: List<WorkoutSessionExerciseResponse>,
)

data class WorkoutSessionExerciseResponse(
    val id: UUID,
    val exerciseId: UUID,
    val exerciseName: String,
    val bodyPart: BodyPart?,
    val sequenceNo: Int,
    val version: Long,
    val sets: List<WorkoutSetResponse>,
    val previousRecord: PreviousExerciseRecordResponse?,
)

data class WorkoutSetResponse(
    val id: UUID,
    val setNo: Int,
    val weightKg: BigDecimal?,
    val repetitions: Int?,
    val durationSeconds: Int?,
    val rpe: Int?,
    val completed: Boolean,
    val memo: String?,
    val version: Long,
)

data class PreviousExerciseRecordResponse(
    val workoutSessionId: UUID,
    val performedAt: Instant,
    val sets: List<WorkoutSetResponse>,
)

data class WorkoutSessionSummaryResponse(
    val id: UUID,
    val routineId: UUID?,
    val routineName: String?,
    val routineSplitDayNo: Int?,
    val routineSplitCount: Int?,
    val routineWorkoutDayNumber: Int?,
    val status: WorkoutSessionStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val exerciseCount: Int,
    val completedSetCount: Int,
    val version: Long,
)

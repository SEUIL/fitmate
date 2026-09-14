package com.fitmate.backend.recommendation.api

import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.EquipmentType
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreateWorkoutPlanRequest(
    val requestId: UUID,
    @field:Min(1) @field:Max(7) val daysPerWeek: Int,
    @field:Min(20) @field:Max(180) val sessionMinutes: Int,
    val equipmentTypes: Set<EquipmentType> = emptySet(),
    @field:Size(max = 100) val preferredExerciseIds: Set<UUID> = emptySet(),
    @field:Size(max = 100) val excludedExerciseIds: Set<UUID> = emptySet(),
    val inbodyRecordId: UUID? = null,
    val externalAiConsent: Boolean = false,
    @field:Size(max = 64) val consentVersion: String? = null,
)

data class PlanExercise(
    val exerciseId: UUID,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)
data class PlanDay(val day: Int, val exercises: List<PlanExercise>)
// Only composition is accepted from the provider. Names, advice and timing come from the server.
data class ProposedWorkoutPlan(val days: List<PlanDay>)
data class PlanCatalogExercise(val id: UUID, val name: String, val bodyPart: BodyPart, val equipmentType: EquipmentType)
data class WorkoutPlanItem(
    val exerciseId: UUID, val exerciseName: String, val bodyPart: BodyPart, val equipmentType: EquipmentType,
    val sets: Int, val reps: Int, val restSeconds: Int, val estimatedSeconds: Int,
)
data class WorkoutPlanDay(
    val day: Int, val exercises: List<WorkoutPlanItem>, val warmupSeconds: Int,
    val cardioSeconds: Int, val estimatedSeconds: Int,
)
data class WorkoutPlanResult(
    val name: String, val source: String, val goal: GoalCode, val skillLevel: SkillLevel,
    val conditions: CreateWorkoutPlanRequest, val inbodyMeasuredDate: LocalDate?,
    val reasons: List<String>, val notices: List<String>, val days: List<WorkoutPlanDay>,
)
data class WorkoutPlanResponse(
    val id: UUID, val status: String, val version: Long, val result: WorkoutPlanResult?,
    val routineId: UUID?, val message: String?,
)
data class ReplacePlanExerciseRequest(
    @param:com.fasterxml.jackson.annotation.JsonProperty(required = true)
    @param:com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL)
    @field:Min(0) val version: Long,
    @field:Min(1) @field:Max(7) val day: Int,
    val exerciseId: UUID, val replacementId: UUID,
)
data class PlanVersionRequest(
    @param:com.fasterxml.jackson.annotation.JsonProperty(required = true)
    @param:com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL)
    @field:Min(0) val version: Long,
)
data class PlanAlternative(val exercise: PlanCatalogExercise, val estimatedDaySeconds: Int, val differenceSeconds: Int)
data class PlanOptions(
    val goal: GoalCode, val skillLevel: SkillLevel, val exercises: List<PlanCatalogExercise>,
    val aiAvailable: Boolean, val consentVersion: String, val consentNotice: String,
)

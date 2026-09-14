package com.fitmate.backend.recommendation.api

import com.fitmate.backend.fitness.domain.EquipmentType
import com.fitmate.backend.routine.application.dto.RoutineItemResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate
import java.util.UUID

data class CreateWorkoutRecommendationRequest(
    @field:Min(1) @field:Max(7) val daysPerWeek: Int,
    @field:Min(20) @field:Max(180) val sessionMinutes: Int,
    val equipmentTypes: Set<EquipmentType> = emptySet(),
    val inbodyRecordId: UUID? = null,
)

data class WorkoutRecommendationResponse(
    val name: String,
    val daysPerWeek: Int,
    val sessionMinutes: Int,
    val splitCount: Int,
    val cardioMinutes: Int,
    val items: List<RoutineItemResponse>,
    val reasons: List<String>,
    val inbodyUsed: Boolean,
    val inbodyMeasuredDate: LocalDate?,
    val aiSummary: String?,
)

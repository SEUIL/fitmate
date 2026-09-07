package com.fitmate.backend.routine.application.dto

import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.routine.domain.DifficultyLevel
import com.fitmate.backend.routine.domain.RoutineSourceType
import java.math.BigDecimal
import java.util.UUID

data class RoutineSummary(
    val id: UUID,
    val ownerUserId: UUID?,
    val sportCode: SportCode,
    val name: String,
    val sourceType: RoutineSourceType,
    val targetGoalCode: GoalCode?,
    val targetLevel: SkillLevel?,
    val difficulty: DifficultyLevel,
    val template: Boolean,
    val version: Long,
)

data class RoutineDetail(
    val id: UUID,
    val ownerUserId: UUID?,
    val sportCode: SportCode,
    val name: String,
    val sourceType: RoutineSourceType,
    val targetGoalCode: GoalCode?,
    val targetLevel: SkillLevel?,
    val difficulty: DifficultyLevel,
    val template: Boolean,
    val version: Long,
    val items: List<RoutineItemResponse>,
)

data class RoutineItemResponse(
    val id: UUID,
    val exerciseId: UUID,
    val exerciseName: String,
    val bodyPart: BodyPart,
    val sequenceNo: Int,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeightKg: BigDecimal?,
    val targetRpe: Int?,
    val restSeconds: Int?,
    val version: Long,
)

package com.fitmate.backend.fitness.application.dto

import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.EquipmentType
import com.fitmate.backend.fitness.domain.SportCode
import java.util.UUID

data class SportSummary(
    val id: UUID,
    val code: SportCode,
    val name: String,
    val displayOrder: Int,
)

data class ExerciseSummary(
    val id: UUID,
    val sportCode: SportCode,
    val name: String,
    val bodyPart: BodyPart,
    val equipmentType: EquipmentType,
    val referenceUrl: String?,
)

data class ExerciseDetail(
    val id: UUID,
    val sportCode: SportCode,
    val name: String,
    val bodyPart: BodyPart,
    val equipmentType: EquipmentType,
    val referenceUrl: String?,
)

data class ExerciseListQuery(
    val sportCode: SportCode?,
    val bodyPart: BodyPart?,
    val equipmentType: EquipmentType?,
    val cursor: String?,
    val size: Int,
)

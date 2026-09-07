package com.fitmate.backend.routine.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.routine.domain.RoutineSourceType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class CreateRoutineRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    val sourceType: RoutineSourceType,
    val sportCode: SportCode? = null,
    val sourceRoutineId: UUID? = null,
    @field:Valid @field:Size(max = 100) val items: List<RoutineItemWriteRequest>? = null,
)

data class UpdateRoutineRequest(
    @param:JsonProperty(required = true) @param:JsonSetter(nulls = Nulls.FAIL) @field:Min(0) val version: Long,
    @field:Size(min = 1, max = 100) val name: String? = null,
    @field:Valid @field:Size(min = 1, max = 100) val items: List<RoutineItemWriteRequest>? = null,
)

data class RoutineItemWriteRequest(
    val exerciseId: UUID,
    @field:Min(1) @field:Max(100) val sequenceNo: Int,
    @field:Min(1) @field:Max(100) val targetSets: Int? = null,
    @field:Min(1) @field:Max(10000) val targetRepsMin: Int? = null,
    @field:Min(1) @field:Max(10000) val targetRepsMax: Int? = null,
    @field:DecimalMin("0.0") val targetWeightKg: BigDecimal? = null,
    @field:Min(1) @field:Max(10) val targetRpe: Int? = null,
    @field:Min(0) @field:Max(3600) val restSeconds: Int? = null,
)

package com.fitmate.backend.fitness.api

import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.fitness.application.FitnessQueryService
import com.fitmate.backend.fitness.application.dto.ExerciseDetail
import com.fitmate.backend.fitness.application.dto.ExerciseListQuery
import com.fitmate.backend.fitness.application.dto.ExerciseSummary
import com.fitmate.backend.fitness.application.dto.SportSummary
import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.EquipmentType
import com.fitmate.backend.fitness.domain.SportCode
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1")
class FitnessQueryController(
    private val fitnessQueryService: FitnessQueryService,
) {

    @GetMapping("/sports")
    fun getSports(): ApiResponse<ItemListResponse<SportSummary>> {
        return ApiResponse.success(
            ItemListResponse(
                items = fitnessQueryService.getSports(),
            ),
        )
    }

    @GetMapping("/exercises")
    fun getExercises(
        @RequestParam(required = false) sportCode: SportCode?,
        @RequestParam(required = false) bodyPart: BodyPart?,
        @RequestParam(required = false) equipmentType: EquipmentType?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ) = ApiResponse.success(
        fitnessQueryService.getExercises(
            ExerciseListQuery(
                sportCode = sportCode,
                bodyPart = bodyPart,
                equipmentType = equipmentType,
                cursor = cursor,
                size = size,
            ),
        ),
    )

    @GetMapping("/exercises/{exerciseId}")
    fun getExercise(
        @PathVariable exerciseId: UUID,
    ): ApiResponse<ExerciseDetail> {
        return ApiResponse.success(fitnessQueryService.getExercise(exerciseId))
    }

    @GetMapping("/exercises/{exerciseId}/substitutions")
    fun getSubstitutions(
        @PathVariable exerciseId: UUID,
    ): ApiResponse<ItemListResponse<ExerciseSummary>> {
        return ApiResponse.success(
            ItemListResponse(
                items = fitnessQueryService.getSubstitutions(exerciseId),
            ),
        )
    }
}

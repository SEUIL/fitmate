package com.fitmate.backend.fitness.application

import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.application.dto.ExerciseDetail
import com.fitmate.backend.fitness.application.dto.ExerciseListQuery
import com.fitmate.backend.fitness.application.dto.ExerciseSummary
import com.fitmate.backend.fitness.application.dto.SportSummary
import com.fitmate.backend.fitness.repository.ExerciseCatalogRepository
import com.fitmate.backend.fitness.repository.ExerciseSubstitutionRepository
import com.fitmate.backend.fitness.repository.SportRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class FitnessQueryService(
    private val sportRepository: SportRepository,
    private val exerciseCatalogRepository: ExerciseCatalogRepository,
    private val exerciseSubstitutionRepository: ExerciseSubstitutionRepository,
) {

    fun getSports(): List<SportSummary> {
        return sportRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
            .map { sport ->
                SportSummary(
                    id = toApiId(sport.id),
                    code = sport.code,
                    name = sport.name,
                    displayOrder = sport.displayOrder,
                )
            }
    }

    fun getExercises(query: ExerciseListQuery): CursorPageResponse<ExerciseSummary> {
        val size = query.size.coerceIn(1, 100)
        val pageIndex = decodeCursor(query.cursor)
        val pageRequest = PageRequest.of(pageIndex, size + 1, Sort.by(Sort.Direction.ASC, "id"))

        val exercises = exerciseCatalogRepository.findActiveExercises(
            sportCode = query.sportCode,
            bodyPart = query.bodyPart,
            equipmentType = query.equipmentType,
            pageable = pageRequest,
        )

        val hasNext = exercises.size > size
        val items = exercises.take(size).map { exercise ->
            ExerciseSummary(
                id = toApiId(exercise.id),
                sportCode = exercise.sport?.code ?: throw IllegalStateException("sport must not be null"),
                name = exercise.name,
                bodyPart = exercise.bodyPart,
                equipmentType = exercise.equipmentType,
                referenceUrl = exercise.referenceUrl,
            )
        }

        return CursorPageResponse(
            items = items,
            nextCursor = if (hasNext) (pageIndex + 1).toString() else null,
            hasNext = hasNext,
        )
    }

    fun getExercise(exerciseId: java.util.UUID): ExerciseDetail {
        val exercise = exerciseCatalogRepository.findActiveExerciseById(toDatabaseId(exerciseId))
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "운동 정보를 찾을 수 없습니다.")

        return ExerciseDetail(
            id = toApiId(exercise.id),
            sportCode = exercise.sport?.code ?: throw IllegalStateException("sport must not be null"),
            name = exercise.name,
            bodyPart = exercise.bodyPart,
            equipmentType = exercise.equipmentType,
            referenceUrl = exercise.referenceUrl,
        )
    }

    fun getSubstitutions(exerciseId: java.util.UUID): List<ExerciseSummary> {
        val databaseId = toDatabaseId(exerciseId)
        exerciseCatalogRepository.findActiveExerciseById(databaseId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "기준 운동 정보를 찾을 수 없습니다.")

        return exerciseSubstitutionRepository.findAllByBaseExerciseId(databaseId)
            .map { substitution ->
                val exercise = substitution.substituteExercise
                    ?: throw IllegalStateException("substitute exercise must not be null")

                ExerciseSummary(
                    id = toApiId(exercise.id),
                    sportCode = exercise.sport?.code ?: throw IllegalStateException("sport must not be null"),
                    name = exercise.name,
                    bodyPart = exercise.bodyPart,
                    equipmentType = exercise.equipmentType,
                    referenceUrl = exercise.referenceUrl,
                )
            }
    }

    private fun decodeCursor(cursor: String?): Int {
        if (cursor.isNullOrBlank()) {
            return 0
        }

        return cursor.toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "cursor는 0 이상의 숫자여야 합니다.")
    }

    private fun toApiId(id: java.util.UUID?): java.util.UUID = UuidBinaryConverter.fromMysqlOrderedUuid(
        id ?: throw IllegalStateException("reference id must not be null"),
    )

    private fun toDatabaseId(id: java.util.UUID): java.util.UUID = UuidBinaryConverter.toMysqlOrderedUuid(id)
}

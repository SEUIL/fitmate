package com.fitmate.backend.fitness.domain

import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class ExerciseSubstitutionId(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "base_exercise_id", columnDefinition = "BINARY(16)")
    var baseExerciseId: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "substitute_exercise_id", columnDefinition = "BINARY(16)")
    var substituteExerciseId: UUID? = null,
) : Serializable

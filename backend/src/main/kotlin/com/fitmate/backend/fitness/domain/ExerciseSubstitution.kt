package com.fitmate.backend.fitness.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table

@Entity
@Table(name = "fitness_exercise_substitutions")
class ExerciseSubstitution(
    @EmbeddedId
    var id: ExerciseSubstitutionId = ExerciseSubstitutionId(),
    @MapsId("baseExerciseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_exercise_id", nullable = false, columnDefinition = "BINARY(16)")
    var baseExercise: ExerciseCatalog? = null,
    @MapsId("substituteExerciseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "substitute_exercise_id", nullable = false, columnDefinition = "BINARY(16)")
    var substituteExercise: ExerciseCatalog? = null,
    @jakarta.persistence.Column(name = "display_order", nullable = false)
    var displayOrder: Short = 0,
) : BaseTimeEntity()

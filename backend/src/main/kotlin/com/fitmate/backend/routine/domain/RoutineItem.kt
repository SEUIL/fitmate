package com.fitmate.backend.routine.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.ExerciseCatalog
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "fitness_routine_items")
class RoutineItem(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_id", nullable = false)
    var routine: Routine? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    var exercise: ExerciseCatalog? = null,
    @Column(name = "sequence_no", nullable = false)
    var sequenceNo: Int = 0,
    @Column(name = "target_sets")
    var targetSets: Int? = null,
    @Column(name = "target_reps_min")
    var targetRepsMin: Int? = null,
    @Column(name = "target_reps_max")
    var targetRepsMax: Int? = null,
    @Column(name = "target_weight_kg", precision = 7, scale = 2)
    var targetWeightKg: BigDecimal? = null,
    @Column(name = "target_rpe")
    var targetRpe: Int? = null,
    @Column(name = "rest_seconds")
    var restSeconds: Int? = null,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) : BaseTimeEntity()

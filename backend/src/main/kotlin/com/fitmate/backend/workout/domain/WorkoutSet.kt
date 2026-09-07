package com.fitmate.backend.workout.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "fitness_workout_sets")
class WorkoutSet(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_exercise_id", nullable = false)
    var sessionExercise: WorkoutSessionExercise? = null,
    @Column(name = "set_no", nullable = false)
    var setNo: Int = 0,
    @Column(name = "weight_kg", precision = 7, scale = 2)
    var weightKg: BigDecimal? = null,
    @Column(name = "repetitions")
    var repetitions: Int? = null,
    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,
    @Column(name = "rest_seconds")
    var restSeconds: Int? = null,
    @Column(name = "rpe")
    var rpe: Int? = null,
    @Column(name = "is_completed", nullable = false)
    var isCompleted: Boolean = false,
    @Column(name = "memo", length = 500)
    var memo: String? = null,
    @Column(name = "client_updated_at", nullable = false)
    var clientUpdatedAt: Instant = Instant.EPOCH,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) : BaseTimeEntity()

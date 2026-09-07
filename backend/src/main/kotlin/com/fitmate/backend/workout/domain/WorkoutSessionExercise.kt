package com.fitmate.backend.workout.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.ExerciseCatalog
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "fitness_workout_session_exercises")
class WorkoutSessionExercise(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_session_id", nullable = false)
    var workoutSession: WorkoutSession? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    var exercise: ExerciseCatalog? = null,
    @Column(name = "display_name_snapshot", nullable = false, length = 100)
    var displayNameSnapshot: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "body_part_snapshot", length = 32)
    var bodyPartSnapshot: BodyPart? = null,
    @Column(name = "sequence_no", nullable = false)
    var sequenceNo: Int = 0,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "client_updated_at", nullable = false)
    var clientUpdatedAt: Instant = Instant.EPOCH,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) : BaseTimeEntity()

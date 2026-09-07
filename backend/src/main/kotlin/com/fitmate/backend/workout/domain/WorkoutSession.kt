package com.fitmate.backend.workout.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.routine.domain.Routine
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
@Table(name = "fitness_workout_sessions")
class WorkoutSession(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    var userId: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    var routine: Routine? = null,
    @Column(name = "routine_split_day_no")
    var routineSplitDayNo: Int? = null,
    @Column(name = "routine_split_count")
    var routineSplitCount: Int? = null,
    @Column(name = "routine_workout_day_number")
    var routineWorkoutDayNumber: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: WorkoutSessionStatus = WorkoutSessionStatus.READY,
    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.EPOCH,
    @Column(name = "ended_at")
    var endedAt: Instant? = null,
    @Column(name = "cancel_reason", length = 255)
    var cancelReason: String? = null,
    @Column(name = "client_updated_at", nullable = false)
    var clientUpdatedAt: Instant = Instant.EPOCH,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) : BaseTimeEntity()

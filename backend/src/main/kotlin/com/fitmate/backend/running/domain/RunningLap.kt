package com.fitmate.backend.running.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "fitness_running_laps")
class RunningLap(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "workout_session_id", nullable = false, columnDefinition = "BINARY(16)")
    var workoutSessionId: UUID? = null,
    @Column(name = "lap_no", nullable = false)
    var lapNo: Int = 0,
    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.EPOCH,
    @Column(name = "ended_at", nullable = false)
    var endedAt: Instant = Instant.EPOCH,
    @Column(name = "distance_meters", nullable = false)
    var distanceMeters: Int = 0,
    @Column(name = "duration_seconds", nullable = false)
    var durationSeconds: Int = 0,
    @Column(name = "avg_pace_sec_per_km", nullable = false)
    var averagePaceSecondsPerKm: Int = 0,
) : BaseTimeEntity()

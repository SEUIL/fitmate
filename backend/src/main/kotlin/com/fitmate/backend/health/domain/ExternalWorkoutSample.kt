package com.fitmate.backend.health.domain

import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "health_external_workout_samples")
class ExternalWorkoutSample(
    @Id @Convert(converter = UuidBinaryConverter::class)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "workout_session_id", nullable = false, columnDefinition = "BINARY(16)")
    var workoutSessionId: UUID? = null,
    @Column(name = "measured_at", nullable = false)
    var measuredAt: Instant = Instant.EPOCH,
    @Column(name = "heart_rate", precision = 6, scale = 2)
    var heartRate: BigDecimal? = null,
    @Column(name = "speed_mps", precision = 8, scale = 3)
    var speedMetersPerSecond: BigDecimal? = null,
)

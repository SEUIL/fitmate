package com.fitmate.backend.health.domain

import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "health_external_workout_summaries")
class ExternalWorkoutSummary(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "workout_session_id", nullable = false, columnDefinition = "BINARY(16)")
    var workoutSessionId: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    var userId: UUID? = null,
    @Column(nullable = false, length = 32)
    var provider: String = PROVIDER,
    @Column(name = "source_record_id", nullable = false, length = 128)
    var sourceRecordId: String = "",
    @Column(name = "source_app_id", length = 255)
    var sourceAppId: String? = null,
    @Column(name = "source_modified_at")
    var sourceModifiedAt: Instant? = null,
    @Column(name = "exercise_type", nullable = false, length = 64)
    var exerciseType: String = "",
    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.EPOCH,
    @Column(name = "ended_at", nullable = false)
    var endedAt: Instant = Instant.EPOCH,
    @Column(name = "active_duration_seconds", nullable = false)
    var activeDurationSeconds: Long = 0,
    @Column(name = "distance_meters", precision = 12, scale = 2)
    var distanceMeters: BigDecimal? = null,
    @Column(name = "calories_kcal", precision = 12, scale = 2)
    var caloriesKcal: BigDecimal? = null,
    @Column(name = "mean_heart_rate", precision = 6, scale = 2)
    var meanHeartRate: BigDecimal? = null,
    @Column(name = "min_heart_rate", precision = 6, scale = 2)
    var minHeartRate: BigDecimal? = null,
    @Column(name = "max_heart_rate", precision = 6, scale = 2)
    var maxHeartRate: BigDecimal? = null,
    @Column(name = "payload_hash", nullable = false, length = 64)
    var payloadHash: String = "",
    @Version
    @Column(nullable = false)
    var version: Long = 0,
    @Column(name = "saved_at", nullable = false)
    var savedAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
) {
    @PrePersist
    fun initializeTimestamps() {
        val now = Instant.now()
        if (savedAt == Instant.EPOCH) savedAt = now
        if (updatedAt == Instant.EPOCH) updatedAt = now
    }

    @PreUpdate
    fun updateTimestamp() { updatedAt = Instant.now() }

    companion object { const val PROVIDER = "SAMSUNG_HEALTH_DATA_SDK" }
}

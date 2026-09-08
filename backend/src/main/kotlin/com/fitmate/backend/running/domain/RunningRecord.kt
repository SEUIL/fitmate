package com.fitmate.backend.running.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.workout.domain.WorkoutSession
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.util.UUID

enum class RunType { FREE, DISTANCE_TARGET, TIME_TARGET, PACE_TARGET, INTERVAL }
enum class RouteVisibility { PUBLIC, PRIVATE }

@Entity
@Table(name = "fitness_running_records")
class RunningRecord(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "workout_session_id", nullable = false, columnDefinition = "BINARY(16)")
    var workoutSessionId: UUID? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id", insertable = false, updatable = false)
    var workoutSession: WorkoutSession? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 32)
    var runType: RunType = RunType.FREE,
    @Column(name = "target_distance_meters")
    var targetDistanceMeters: Int? = null,
    @Column(name = "target_duration_seconds")
    var targetDurationSeconds: Int? = null,
    @Column(name = "target_pace_sec_per_km")
    var targetPaceSecondsPerKm: Int? = null,
    @Column(name = "interval_seconds")
    var intervalSeconds: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "route_visibility", nullable = false, length = 16)
    var routeVisibility: RouteVisibility = RouteVisibility.PRIVATE,
    @Column(name = "auto_pause_enabled", nullable = false)
    var autoPauseEnabled: Boolean = false,
    @Column(name = "active_duration_seconds", nullable = false)
    var activeDurationSeconds: Int = 0,
    @Column(name = "total_distance_meters", nullable = false)
    var totalDistanceMeters: Int = 0,
    @Column(name = "avg_pace_sec_per_km")
    var averagePaceSecondsPerKm: Int? = null,
    @Column(name = "best_pace_sec_per_km")
    var bestPaceSecondsPerKm: Int? = null,
    @Column(name = "avg_speed_kmh", precision = 10, scale = 2)
    var averageSpeedKmh: BigDecimal? = null,
    @Column(name = "calories_kcal", precision = 8, scale = 2)
    var caloriesKcal: BigDecimal? = null,
    @Column(name = "cadence_spm")
    var cadenceSpm: Int? = null,
    @Column(name = "stride_length_cm", precision = 6, scale = 2)
    var strideLengthCm: BigDecimal? = null,
    @Column(name = "elevation_gain_meters", precision = 8, scale = 2)
    var elevationGainMeters: BigDecimal? = null,
    @Column(name = "rpe")
    var rpe: Int? = null,
    @Column(name = "condition_score")
    var conditionScore: Int? = null,
    @Column(name = "memo", length = 500)
    var memo: String? = null,
    @Column(name = "route_json", columnDefinition = "MEDIUMTEXT")
    var routeJson: String? = null,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) : BaseTimeEntity()

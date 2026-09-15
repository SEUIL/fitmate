package com.fitmate.backend.sportrecord.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.workout.domain.WorkoutSession
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "fitness_sport_records")
class SportRecord(
    @Id @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "workout_session_id", columnDefinition = "BINARY(16)")
    var workoutSessionId: UUID? = null,
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "workout_session_id", insertable = false, updatable = false)
    var workoutSession: WorkoutSession? = null,
    @Column(name = "activity_type", nullable = false, length = 32) var activityType: String = "",
    @Column(name = "active_duration_seconds", nullable = false) var activeDurationSeconds: Int = 0,
    @Column(name = "place_name", length = 100) var placeName: String? = null,
    @Column(length = 500) var memo: String? = null,
    var gameCount: Int? = null, var winCount: Int? = null,
    var attemptCount: Int? = null, var completionCount: Int? = null,
    @Column(length = 100) var difficultyMemo: String? = null,
    @Column(length = 100) var courseName: String? = null,
    var distanceMeters: Int? = null, var shotCount: Int? = null, var holeCount: Int? = null, var strokeCount: Int? = null,
) : BaseTimeEntity()

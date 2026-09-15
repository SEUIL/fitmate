package com.fitmate.backend.sportrecord.api

import com.fitmate.backend.fitness.domain.SportCode
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateSportRecordRequest(
    @field:NotNull val clientRecordId: UUID?,
    @field:NotNull val sportCode: SportCode?,
    @field:NotNull val activityType: String?,
    @field:NotNull val startedAt: Instant?,
    @field:NotNull val completedAt: Instant?,
    @field:NotNull @field:Min(1) val activeDurationSeconds: Int?,
    @field:Size(max = 100) val placeName: String? = null,
    @field:Size(max = 500) val memo: String? = null,
    @field:Min(0) @field:Max(9999) val gameCount: Int? = null,
    @field:Min(0) @field:Max(9999) val winCount: Int? = null,
    @field:Min(0) @field:Max(9999) val attemptCount: Int? = null,
    @field:Min(0) @field:Max(9999) val completionCount: Int? = null,
    @field:Size(max = 100) val difficultyMemo: String? = null,
    @field:Size(max = 100) val courseName: String? = null,
    @field:Min(0) @field:Max(1_000_000) val distanceMeters: Int? = null,
    @field:Min(0) @field:Max(9999) val shotCount: Int? = null,
    @field:Min(1) @field:Max(18) val holeCount: Int? = null,
    @field:Min(1) @field:Max(9999) val strokeCount: Int? = null,
)

data class SportRecordResponse(
    val id: UUID, val sportCode: SportCode, val activityType: String,
    val startedAt: Instant, val completedAt: Instant, val activeDurationSeconds: Int,
    val elapsedDurationSeconds: Long, val placeName: String?, val memo: String?,
    val gameCount: Int?, val winCount: Int?, val attemptCount: Int?, val completionCount: Int?,
    val difficultyMemo: String?, val courseName: String?, val distanceMeters: Int?,
    val shotCount: Int?, val holeCount: Int?, val strokeCount: Int?,
)

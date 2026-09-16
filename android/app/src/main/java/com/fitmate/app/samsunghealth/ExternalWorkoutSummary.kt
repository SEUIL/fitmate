package com.fitmate.app.samsunghealth

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit

internal const val SAMSUNG_HEALTH_PROVIDER = "SAMSUNG_HEALTH_DATA_SDK"

data class ExternalWorkoutSummary(
    val provider: String = SAMSUNG_HEALTH_PROVIDER,
    val sourceRecordId: String,
    val sourceAppId: String?,
    val sourceModifiedAt: Instant?,
    val exerciseType: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val activeDurationSeconds: Long,
    val distanceMeters: BigDecimal?,
    val caloriesKcal: BigDecimal?,
    val meanHeartRate: BigDecimal?,
    val minHeartRate: BigDecimal?,
    val maxHeartRate: BigDecimal?,
) {
    fun normalized() = copy(
        sourceModifiedAt = sourceModifiedAt?.truncatedTo(ChronoUnit.MILLIS),
        startedAt = startedAt.truncatedTo(ChronoUnit.MILLIS),
        endedAt = endedAt.truncatedTo(ChronoUnit.MILLIS),
        distanceMeters = distanceMeters.normalizedMetric(),
        caloriesKcal = caloriesKcal.normalizedMetric(),
        meanHeartRate = meanHeartRate.normalizedMetric(),
        minHeartRate = minHeartRate.normalizedMetric(),
        maxHeartRate = maxHeartRate.normalizedMetric(),
    )
}

fun SamsungWorkoutUiModel.toExternalWorkoutSummary() = ExternalWorkoutSummary(
    sourceRecordId = uid,
    sourceAppId = sourceAppId?.takeIf { it.isNotEmpty() },
    sourceModifiedAt = sourceModifiedAt,
    exerciseType = exerciseType,
    startedAt = startTime,
    endedAt = endTime,
    activeDurationSeconds = activeDurationSeconds,
    distanceMeters = distanceMeters?.toString()?.toBigDecimalOrNull(),
    caloriesKcal = calories?.toString()?.toBigDecimalOrNull(),
    meanHeartRate = meanHeartRate?.toString()?.toBigDecimalOrNull(),
    minHeartRate = minHeartRate?.toString()?.toBigDecimalOrNull(),
    maxHeartRate = maxHeartRate?.toString()?.toBigDecimalOrNull(),
).normalized()

enum class ExternalWorkoutRecheck {
    UNCHANGED,
    MODIFIED,
    MISSING,
}

enum class ExternalWorkoutLookupState {
    FOUND,
    NOT_FOUND,
    PERMISSION_ERROR,
    PLATFORM_ERROR,
    TEMPORARY_FAILURE,
}

data class ExternalWorkoutLookupResult(
    val state: ExternalWorkoutLookupState,
    val workout: SamsungWorkoutUiModel? = null,
) {
    /** A missing source may be unlinked only after a separate, explicit user confirmation. */
    fun allowsExplicitUnlink(userConfirmed: Boolean): Boolean =
        state == ExternalWorkoutLookupState.NOT_FOUND && userConfirmed
}

fun classifyExternalWorkoutLookup(
    state: SamsungHealthUiState,
    sourceRecordId: String,
    sourceAppId: String?,
): ExternalWorkoutLookupResult {
    val lookupState = when (state.status) {
        SamsungHealthStatus.PERMISSION_MISSING,
        SamsungHealthStatus.AUTHORIZATION_ERROR,
        -> ExternalWorkoutLookupState.PERMISSION_ERROR
        SamsungHealthStatus.PLATFORM_ERROR -> ExternalWorkoutLookupState.PLATFORM_ERROR
        SamsungHealthStatus.LOADING,
        SamsungHealthStatus.UNKNOWN_ERROR,
        -> ExternalWorkoutLookupState.TEMPORARY_FAILURE
        SamsungHealthStatus.READY,
        SamsungHealthStatus.DATA_EMPTY,
        -> null
    }
    if (lookupState != null) return ExternalWorkoutLookupResult(lookupState)
    if (state.permissions.any { it.label == "운동 읽기" && !it.granted }) {
        return ExternalWorkoutLookupResult(ExternalWorkoutLookupState.PERMISSION_ERROR)
    }
    val found = state.workouts.firstOrNull { it.uid == sourceRecordId && it.sourceAppId == sourceAppId }
    return if (found == null) ExternalWorkoutLookupResult(ExternalWorkoutLookupState.NOT_FOUND)
    else ExternalWorkoutLookupResult(ExternalWorkoutLookupState.FOUND, found)
}

fun recheckExternalWorkoutSnapshot(
    selected: SamsungWorkoutUiModel,
    refreshed: SamsungWorkoutUiModel?,
): ExternalWorkoutRecheck = when {
    refreshed == null -> ExternalWorkoutRecheck.MISSING
    selected.toExternalWorkoutSummary() == refreshed.toExternalWorkoutSummary() -> ExternalWorkoutRecheck.UNCHANGED
    else -> ExternalWorkoutRecheck.MODIFIED
}

private fun BigDecimal?.normalizedMetric() = this?.setScale(2, RoundingMode.HALF_UP)

package com.fitmate.app.data.remote

import com.fitmate.app.samsunghealth.ExternalWorkoutSummary
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.time.Instant

internal data class StoredExternalWorkout(
    val sessionId: String,
    val summary: ExternalWorkoutSummary,
    val version: Long,
    val savedAt: Instant,
    val updatedAt: Instant,
)

internal fun externalWorkoutContentMatches(expected: ExternalWorkoutSummary, actual: StoredExternalWorkout): Boolean =
    expected.normalized() == actual.summary.normalized()

internal fun JsonObject.toStoredExternalWorkout() = StoredExternalWorkout(
    sessionId = requiredText("sessionId"),
    summary = ExternalWorkoutSummary(
        provider = requiredText("provider"),
        sourceRecordId = requiredText("sourceRecordId"),
        sourceAppId = nullableText("sourceAppId"),
        sourceModifiedAt = nullableText("sourceModifiedAt")?.let(Instant::parse),
        exerciseType = requiredText("exerciseType"),
        startedAt = Instant.parse(requiredText("startedAt")),
        endedAt = Instant.parse(requiredText("endedAt")),
        activeDurationSeconds = get("activeDurationSeconds").asLong,
        distanceMeters = nullableDecimal("distanceMeters"),
        caloriesKcal = nullableDecimal("caloriesKcal"),
        meanHeartRate = nullableDecimal("meanHeartRate"),
        minHeartRate = nullableDecimal("minHeartRate"),
        maxHeartRate = nullableDecimal("maxHeartRate"),
    ).normalized(),
    version = get("version").asLong,
    savedAt = Instant.parse(requiredText("savedAt")),
    updatedAt = Instant.parse(requiredText("updatedAt")),
)

private fun JsonObject.requiredText(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString
    ?: error("외부 운동 응답에 $key 값이 없습니다.")
private fun JsonObject.nullableText(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString
private fun JsonObject.nullableDecimal(key: String): BigDecimal? = get(key)?.takeUnless { it.isJsonNull }?.asString?.toBigDecimal()

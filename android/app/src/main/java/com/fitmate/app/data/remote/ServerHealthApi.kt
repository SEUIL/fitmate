package com.fitmate.app.data.remote

import com.fitmate.app.samsunghealth.ExternalWorkoutSummary
import com.google.gson.JsonNull

internal class ServerHealthApi(private val api: FitMateApiClient) {
    fun get(sessionId: String): StoredExternalWorkout? {
        val data = api.request("GET", "workout-sessions/$sessionId/external-workout")
        return if (data is JsonNull || data.isJsonNull) null else data.asJsonObject.toStoredExternalWorkout()
    }

    fun put(sessionId: String, summary: ExternalWorkoutSummary, expectedVersion: Long?): StoredExternalWorkout {
        val normalized = summary.normalized()
        val body = linkedMapOf<String, Any?>(
            "provider" to normalized.provider,
            "sourceRecordId" to normalized.sourceRecordId,
            "sourceAppId" to normalized.sourceAppId,
            "sourceModifiedAt" to normalized.sourceModifiedAt?.toString(),
            "exerciseType" to normalized.exerciseType,
            "startedAt" to normalized.startedAt.toString(),
            "endedAt" to normalized.endedAt.toString(),
            "activeDurationSeconds" to normalized.activeDurationSeconds,
            "distanceMeters" to normalized.distanceMeters,
            "caloriesKcal" to normalized.caloriesKcal,
            "meanHeartRate" to normalized.meanHeartRate,
            "minHeartRate" to normalized.minHeartRate,
            "maxHeartRate" to normalized.maxHeartRate,
            "expectedVersion" to expectedVersion,
        )
        return api.request("PUT", "workout-sessions/$sessionId/external-workout", body).asJsonObject.toStoredExternalWorkout()
    }
}

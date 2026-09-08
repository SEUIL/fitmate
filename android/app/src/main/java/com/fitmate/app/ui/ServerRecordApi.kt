package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import java.time.Instant

/** Completed records use the same authenticated client as live workout sessions. */
internal class ServerRecordApi(private val api: FitMateApiClient) {
    fun page(path: String, cursor: String = "0"): JsonObject =
        api.request("GET", "$path${if ('?' in path) '&' else '?'}cursor=$cursor&size=20").asJsonObject

    fun run(id: String) = api.request("GET", "running-records/$id").asJsonObject
    fun report(id: String) = api.request("GET", "workout-sessions/$id/report").asJsonObject
    fun session(id: String) = api.request("GET", "workout-sessions/$id").asJsonObject
    fun saveRun(body: JsonObject) = api.request("POST", "running-records", body).asJsonObject
}

internal fun runningPayload(state: RunningState, completedAt: Long): Map<String, Any> = with(state) {
    require(seconds > 0) { "1초 이상 운동한 후 저장해 주세요." }
    val result = mutableMapOf<String, Any>(
        "runType" to when (goal) {
            "거리" -> "DISTANCE_TARGET"; "시간" -> "TIME_TARGET"; "페이스" -> "PACE_TARGET"
            "인터벌" -> "INTERVAL"; else -> "FREE"
        },
        "activeDurationSeconds" to seconds, "distanceMeters" to meters,
        "completedAt" to Instant.ofEpochMilli(completedAt).toString(),
        "routeVisibility" to "PRIVATE", "autoPauseEnabled" to autoPause,
        "rpe" to rpe.toInt(), "conditionScore" to condition.toInt(), "memo" to memo,
        "laps" to laps.map { mapOf("lapNo" to it.number, "distanceMeters" to it.meters, "durationSeconds" to it.seconds) }
    )
    if (goal in listOf("거리", "페이스")) result["targetDistanceMeters"] = target
    if (goal in listOf("시간", "인터벌")) result["targetDurationSeconds"] = target
    if (goal == "페이스") result["targetPaceSecondsPerKm"] = targetPace.toInt()
    if (goal == "인터벌") result["intervalSeconds"] = intervalMinutes.toInt() * 60
    result
}

internal fun JsonObject.recordInt(key: String): Int? = get(key)?.takeUnless { it.isJsonNull }?.asInt
internal fun JsonObject.recordTime(key: String): Long? = text(key).takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilli() }

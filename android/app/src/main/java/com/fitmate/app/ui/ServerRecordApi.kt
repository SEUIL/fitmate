package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import java.time.Instant

/** Completed records use the same authenticated client as live workout sessions. */
internal class ServerRecordApi(private val api: FitMateApiClient) {
    fun page(path: String, cursor: String = "0", size: Int = 20): JsonObject =
        api.request("GET", "$path${if ('?' in path) '&' else '?'}cursor=$cursor&size=$size").asJsonObject

    /** 프로필 상단 표시에 사용할 완료 기록 수입니다. 페이지 전체를 합산합니다. */
    fun completedCount(path: String): Int {
        var cursor = "0"
        var count = 0
        repeat(10_000) {
            val result = page(path, cursor, size = 100)
            count += result.rows("items").size
            val next = result.text("nextCursor")
            if (result.get("hasNext")?.asBoolean != true || next.isBlank() || next == cursor) return count
            cursor = next
        }
        error("운동 기록 페이지를 모두 불러오지 못했습니다.")
    }

    fun run(id: String) = api.request("GET", "running-records/$id").asJsonObject
    fun report(id: String) = api.request("GET", "workout-sessions/$id/report").asJsonObject
    fun session(id: String) = api.request("GET", "workout-sessions/$id").asJsonObject
    fun saveRun(body: JsonObject) = api.request("POST", "running-records", body).asJsonObject
    fun deleteRun(id: String) = api.request("DELETE", "running-records/$id")
}

internal fun runningPayload(state: RunningState, completedAt: Long): Map<String, Any> = with(state) {
    require(seconds > 0) { "1초 이상 운동한 후 저장해 주세요." }
    val started = requireNotNull(startedAt) { "러닝 시작 시각이 없습니다. 새 기록을 시작해 주세요." }
    require(completedAt >= started + seconds * 1_000L) { "완료 시각은 활동 시간보다 이전일 수 없습니다." }
    val result = mutableMapOf<String, Any>(
        "runType" to when (goal) {
            "거리" -> "DISTANCE_TARGET"; "시간" -> "TIME_TARGET"; "페이스" -> "PACE_TARGET"
            "인터벌" -> "INTERVAL"; else -> "FREE"
        },
        "activeDurationSeconds" to seconds, "distanceMeters" to meters,
        "startedAt" to Instant.ofEpochMilli(started).toString(),
        "completedAt" to Instant.ofEpochMilli(completedAt).toString(),
        "routeVisibility" to "PRIVATE", "autoPauseEnabled" to autoPause,
        "rpe" to rpe.toInt(), "conditionScore" to condition.toInt(), "memo" to memo,
        "laps" to laps.map { mapOf("lapNo" to it.number, "distanceMeters" to it.meters, "durationSeconds" to it.seconds) }
    )
    if (goal in listOf("거리", "페이스")) result["targetDistanceMeters"] = target
    if (goal in listOf("시간", "인터벌")) result["targetDurationSeconds"] = target
    if (goal == "페이스") result["targetPaceSecondsPerKm"] = targetPace.toInt()
    if (goal == "인터벌") result["intervalSeconds"] = intervalMinutes.toInt() * 60
    if (recordRoute && routePoints.isNotEmpty()) result["routePoints"] = routePoints.map { point ->
        buildMap<String, Any> {
            put("latitude", point.latitude)
            put("longitude", point.longitude)
            put("recordedAt", Instant.ofEpochMilli(point.recordedAtEpochMillis).toString())
            point.altitudeMeters?.let { put("altitudeMeters", it) }
        }
    }
    result
}

internal fun JsonObject.recordInt(key: String): Int? = get(key)?.takeUnless { it.isJsonNull }?.asInt
internal fun JsonObject.recordTime(key: String): Long? = text(key).takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilli() }

package com.fitmate.app.ui

import com.fitmate.app.data.model.WorkoutSet
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class WorkoutUpload(val sets: List<WorkoutSet>, val running: Boolean)

/** Serializes each session's writes and retains server revisions for conflict detection. */
internal class ServerWorkout(private val api: FitMateApiClient, private val session: WorkoutSession) {
    private fun state(): JsonObject = JsonParser.parseString(requireNotNull(session.serverState)).asJsonObject
    private fun save(value: JsonObject) { session.serverState = value.toString() }

    @Synchronized fun synchronize(upload: WorkoutUpload) {
        var current = state()
        if (current.text("status") == "COMPLETED") return
        val knownNames = current.rows("exercises").map { it.text("exerciseName") }.toSet()
        require(upload.sets.all { it.exerciseName in knownNames }) { "운동 시작 후 추가한 운동은 서버에 등록되지 않았습니다. 해당 기록을 별도로 보관한 후 시작할 때 선택한 운동만 저장하세요." }
        if (current.text("status") == "READY") current = transition(current, "start")
        if (current.text("status") == "PAUSED" && (upload.running || !sameSets(current, upload.sets))) current = transition(current, "resume")
        if (current.text("status") == "IN_PROGRESS") {
            current.rows("exercises").forEach { exercise ->
                val desired = upload.sets.filter { it.exerciseName == exercise.text("exerciseName") }
                val existing = exercise.rows("sets").associateBy { it.get("setNo").asInt }
                val updated = existing.toMutableMap()
                for (number in 1..maxOf(desired.size, existing.keys.maxOrNull() ?: 0)) {
                    val row = existing[number]
                    val item = desired.getOrNull(number - 1)
                    if (matches(row, item)) continue
                    val body = mutableMapOf<String, Any>("completed" to (item != null))
                    row?.get("version")?.let { body["version"] = it.asLong }
                    item?.let { body["weightKg"] = it.weightKg; body["repetitions"] = it.repetitions; body["memo"] = it.memo; it.rpe?.let { rpe -> body["rpe"] = rpe } }
                    val saved = api.request("PUT", "workout-sessions/${current.text("id")}/exercises/${exercise.text("id")}/sets/$number", body).asJsonObject
                    updated[number] = saved
                    exercise.add("sets", com.google.gson.JsonArray().also { list -> updated.toSortedMap().values.forEach { list.add(it) } })
                    save(current)
                }
            }
            if (!upload.running) current = transition(current, "pause")
        }
        save(current)
    }

    @Synchronized fun complete(upload: WorkoutUpload) {
        require(upload.sets.isNotEmpty()) { "완료한 세트를 하나 이상 기록하세요." }
        synchronize(upload)
        val current = state()
        if (current.text("status") != "COMPLETED") transition(current, "complete")
        // Read back the committed session before the caller leaves the workout screen.
        save(api.request("GET", "workout-sessions/${state().text("id")}").asJsonObject)
    }

    private fun transition(current: JsonObject, action: String): JsonObject =
        api.request("POST", "workout-sessions/${current.text("id")}/$action", mapOf("version" to current.get("version").asLong)).asJsonObject.also(::save)

    private fun sameSets(current: JsonObject, desired: List<WorkoutSet>): Boolean = current.rows("exercises").all { exercise ->
        val rows = exercise.rows("sets").associateBy { it.get("setNo").asInt }
        val next = desired.filter { it.exerciseName == exercise.text("exerciseName") }
        (1..maxOf(next.size, rows.keys.maxOrNull() ?: 0)).all { matches(rows[it], next.getOrNull(it - 1)) }
    }

    private fun matches(row: JsonObject?, item: WorkoutSet?): Boolean {
        if (item == null) return row == null || !row.get("completed").asBoolean
        if (row == null || !row.get("completed").asBoolean) return false
        return row.get("weightKg")?.takeUnless { it.isJsonNull }?.asDouble == item.weightKg &&
            row.get("repetitions")?.takeUnless { it.isJsonNull }?.asInt == item.repetitions &&
            row.get("rpe")?.takeUnless { it.isJsonNull }?.asInt == item.rpe && row.text("memo") == item.memo
    }
}

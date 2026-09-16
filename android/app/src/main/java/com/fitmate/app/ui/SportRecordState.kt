package com.fitmate.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fitmate.app.data.model.Sport
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal val additionalSports = listOf(Sport.BADMINTON, Sport.CLIMBING, Sport.HIKING, Sport.GOLF)
internal fun sportLabel(code: String) = if (code == "HEALTH") "헬스" else Sport.entries.firstOrNull { it.name == code }?.label ?: code
internal fun sportTypes(code: String): Map<String, String> = when (code) {
    "BADMINTON" -> linkedMapOf("SINGLES" to "단식", "DOUBLES" to "복식", "PRACTICE" to "연습")
    "CLIMBING" -> linkedMapOf("BOULDERING" to "볼더링", "LEAD" to "리드", "TOP_ROPE" to "탑로프")
    "HIKING" -> linkedMapOf("HIKE" to "등산", "TREKKING" to "트레킹")
    "GOLF" -> linkedMapOf("PRACTICE" to "연습", "SCREEN" to "스크린", "FIELD" to "필드")
    else -> emptyMap()
}
internal data class SportField(val key: String, val label: String, val max: Int = 9999, val min: Int = 0, val text: Boolean = false)
internal fun sportFields(code: String, type: String) = when (code) {
    "BADMINTON" -> listOf(SportField("gameCount", "게임 수"), SportField("winCount", "승리 수"))
    "CLIMBING" -> listOf(SportField("attemptCount", "시도 수"), SportField("completionCount", "완등 수"), SportField("difficultyMemo", "난이도 메모", text = true))
    "HIKING" -> listOf(SportField("courseName", "산·코스명", text = true), SportField("distanceMeters", "거리 (km)", max = 1_000_000))
    "GOLF" -> if (type == "PRACTICE") listOf(SportField("shotCount", "타격 수")) else listOf(SportField("holeCount", "진행 홀 수", 18, 1), SportField("strokeCount", "총 타수", min = 1))
    else -> emptyList()
}

internal data class SportDraft(
    val sport: String, val type: String, val phase: String = "준비",
    val id: String? = null, val started: Long? = null, val ended: Long? = null,
    val accumulated: Long = 0, val anchor: Long? = null, val checkpoint: Long = 0,
    val boot: Int = -1, val place: String = "", val memo: String = "",
    val fields: Map<String, String> = emptyMap(), val pending: String? = null,
) {
    fun millis(now: Long): Long = accumulated + (anchor?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun restore(now: Long, currentBoot: Int): SportDraft =
        if (anchor != null && (boot < 0 || boot != currentBoot || now < checkpoint)) copy(anchor = null) else this
    fun checkpoint(now: Long) = copy(accumulated = millis(now), anchor = anchor?.let { now }, checkpoint = now)
    fun changeType(value: String) = copy(type = value, fields = emptyMap())
    fun errors(): Map<String, String> = buildMap {
        sportFields(sport, type).forEach { field ->
            val input = fields[field.key].orEmpty().trim()
            if (input.isNotEmpty()) {
                if (field.text) { if (input.length > 100) put(field.key, "100자 이내로 입력해주세요.") }
                else {
                    val value = if (field.key == "distanceMeters") distanceInMeters(input) else input.toIntOrNull()
                    if (value == null || value !in field.min..field.max) put(field.key,
                        if (field.key == "distanceMeters") "0~1,000 km, 소수점 셋째 자리까지 입력해주세요." else "${field.min}~${field.max} 사이의 정수를 입력해주세요.")
                }
            }
        }
        listOf("winCount" to "gameCount", "completionCount" to "attemptCount", "strokeCount" to "holeCount").forEach { (child, parent) ->
            if (!fields[child].isNullOrBlank() && fields[parent].isNullOrBlank()) put(parent, "결과를 기록하려면 이 항목도 입력해주세요.")
            if (child != "strokeCount") {
                val a = fields[child]?.toIntOrNull(); val b = fields[parent]?.toIntOrNull()
                if (a != null && b != null && a > b) put(child, "전체 횟수를 넘을 수 없어요.")
            }
        }
    }
    fun payload(): JsonObject {
        require(errors().isEmpty()) { "입력한 결과를 확인해주세요." }
        require(sportTypes(sport).containsKey(type))
        val start = requireNotNull(started); val end = requireNotNull(ended)
        val seconds = accumulated / 1000
        require(seconds in 1..Int.MAX_VALUE.toLong() && end > start && end - start >= seconds * 1000) { "1초 이상 운동하고 기기 날짜·시간을 확인해주세요." }
        return JsonObject().apply {
            addProperty("clientRecordId", requireNotNull(id)); addProperty("sportCode", sport); addProperty("activityType", type)
            addProperty("startedAt", Instant.ofEpochMilli(start).toString()); addProperty("completedAt", Instant.ofEpochMilli(end).toString())
            addProperty("activeDurationSeconds", seconds)
            if (place.isNotBlank()) addProperty("placeName", place.trim())
            if (memo.isNotBlank()) addProperty("memo", memo.trim())
            sportFields(sport, type).forEach { f -> fields[f.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                if (f.text) addProperty(f.key, value) else addProperty(f.key, if (f.key == "distanceMeters") distanceInMeters(value) else value.toInt())
            } }
        }
    }
}
internal fun distanceInMeters(input: String): Int? = runCatching {
    require(Regex("[0-9]+(?:\\.[0-9]{1,3})?").matches(input))
    BigDecimal(input).multiply(BigDecimal(1000)).intValueExact()
}.getOrNull()

internal class SportRecordState(initial: SportDraft? = null) {
    var draft by mutableStateOf(initial)
    var restoreNotice by mutableStateOf(false)
    fun prepare(sport: String) { check(draft == null); draft = SportDraft(sport, sportTypes(sport).keys.first()) }
    fun start(wall: Long, now: Long, boot: Int) {
        val d = requireNotNull(draft); check(d.phase == "준비")
        draft = d.copy(phase = "진행", id = UUID.randomUUID().toString(), started = wall, anchor = now, checkpoint = now, boot = boot)
    }
    fun pause(now: Long) { draft = draft?.let { it.checkpoint(now).copy(anchor = null) } }
    fun resume(now: Long) { draft = draft?.let { check(it.phase == "진행" && it.anchor == null); it.copy(anchor = now, checkpoint = now) } }
    fun finish(wall: Long, now: Long) { draft = draft?.let { it.checkpoint(now).copy(phase = "결과", ended = wall, anchor = null) } }
}

/** Compare normalized nullable fields before treating a recovered POST as successful. */
internal fun sportRecordMatches(body: JsonObject, record: JsonObject): Boolean {
    if (body.text("clientRecordId") != record.text("id")) return false
    val keys = listOf("sportCode", "activityType", "startedAt", "completedAt", "activeDurationSeconds", "placeName", "memo") +
        sportFields(body.text("sportCode"), body.text("activityType")).map { it.key }
    return keys.all { key ->
        if (key == "startedAt" || key == "completedAt") runCatching { Instant.parse(body.text(key)) == Instant.parse(record.text(key)) }.getOrDefault(false)
        else body.text(key) == record.text(key)
    }
}

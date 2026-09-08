package com.fitmate.app.data.remote

import com.fitmate.app.data.model.MateDetails
import com.fitmate.app.data.model.MatePost
import com.fitmate.app.data.model.Sport
import com.google.gson.JsonObject
import java.util.UUID
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.net.URLEncoder
import com.fitmate.app.data.model.RecruitmentDraft

/** 메이트 모집글 서버 응답을 기존 화면 모델에 안전하게 변환한다. */
internal class ServerMateApi(private val api: FitMateApiClient) {
    fun posts(): List<MatePost> = api.items("mate-posts").map(::toMatePost)
    fun page(cursor: String? = null): JsonObject = api.request("GET", "mate-posts?size=30" + cursor?.let { "&cursor=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()).asJsonObject
    fun detail(id: String) = api.request("GET", "mate-posts/$id").asJsonObject
    fun create(body: Map<String, Any>, key: String) = api.request("POST", "mate-posts", body, headers = mapOf("Idempotency-Key" to key)).asJsonObject
    fun update(id: String, body: Map<String, Any>) = api.request("PATCH", "mate-posts/$id", body).asJsonObject
    fun action(id: String, action: String, version: Long) = api.request("POST", "mate-posts/$id/$action", mapOf("version" to version))
    fun apply(id: String) = api.request("POST", "mate-posts/$id/applications")
    fun applications(id: String) = allPages("mate-posts/$id/applications")
    fun decide(post: String, id: String, action: String, version: Long) =
        api.request("POST", "mate-posts/$post/applications/$id/$action", mapOf("version" to version))
    fun openRoom(post: String) = api.request("POST", "mate-posts/$post/inquiry-room").asJsonObject
    fun rooms() = allPages("mate-chat-rooms")
    fun room(id: String) = api.request("GET", "mate-chat-rooms/$id").asJsonObject
    fun messages(id: String, after: Long) = api.items("mate-chat-rooms/$id/messages?size=100&afterSequence=$after")
    fun send(id: String, body: String, clientId: String) = api.request("POST", "mate-chat-rooms/$id/messages", mapOf("body" to body, "clientMessageId" to clientId)).asJsonObject
    fun read(id: String, message: String) = api.request("PUT", "mate-chat-rooms/$id/read", mapOf("lastReadMessageId" to message))
    private fun allPages(path: String): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val page = api.request("GET", "$path?size=100" + cursor?.let { "&cursor=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()).asJsonObject
            rows += page.getAsJsonArray("items").map { it.asJsonObject }
            cursor = if (page.get("hasNext").asBoolean) page.optionalText("nextCursor") else null
            check(cursor == null || seen.add(cursor)) { "서버 페이지 정보가 올바르지 않습니다." }
        } while (cursor != null)
        return rows
    }
}

internal fun mateCreateBody(draft: RecruitmentDraft, regionId: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Map<String, Any> {
    UUID.fromString(regionId)
    val parts = draft.dateTime.split(" ")
    val date = when (parts.first()) {
        "오늘" -> today
        "내일" -> today.plusDays(1)
        "토요일" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        "일요일" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        else -> LocalDate.parse(parts.first())
    }
    val start = date.atTime(LocalTime.parse(parts.last())).atZone(zone).toInstant()
    val hours = Regex("(\\d+)시간").find(draft.duration)?.groupValues?.get(1)?.toInt() ?: 0
    val minutes = Regex("(\\d+)분").find(draft.duration)?.groupValues?.get(1)?.toInt() ?: 0
    return mapOf("title" to draft.title.trim(), "body" to draft.description.trim(),
        "sportCode" to if (draft.sport == Sport.FITNESS) "HEALTH" else draft.sport.name,
        "regionId" to regionId, "scheduledStartAt" to start.toString(),
        "durationSeconds" to (hours * 3600 + minutes * 60),
        "locationSummary" to draft.details.location.trim(), "capacity" to draft.capacity,
        "difficulty" to when (draft.difficulty) { "초보" -> "BEGINNER"; "중급+" -> "ADVANCED"; else -> "INTERMEDIATE" },
        "estimatedCostKrw" to draft.details.cost.toInt(), "meetingPoint" to draft.details.meetingPoint.trim(),
        "visibility" to if (draft.details.visibility == "맞팔 공개") "MUTUAL" else "PUBLIC",
        "sportFields" to draft.details.sportFields.toSortedMap(), "beginnerWelcome" to draft.isBeginnerWelcome)
}

internal fun toMatePost(row: JsonObject): MatePost {
    val serverId = row.requiredText("id")
    val scheduledAt = row.optionalText("scheduledStartAt").orEmpty()
    val durationSeconds = row.optionalInt("durationSeconds")
    val sport = when (row.optionalText("sportCode")) {
        "RUNNING" -> Sport.RUNNING
        "BADMINTON" -> Sport.BADMINTON
        "CLIMBING" -> Sport.CLIMBING
        "HIKING" -> Sport.HIKING
        "GOLF" -> Sport.GOLF
        else -> Sport.FITNESS
    }
    return MatePost(
        // 기존 화면 상태가 Long 키를 사용하므로 UUID의 상위 63비트를 안정적인 UI 키로 쓴다.
        id = UUID.fromString(serverId).mostSignificantBits and Long.MAX_VALUE,
        title = row.requiredText("title"),
        sport = sport,
        region = row.optionalText("locationSummary").orEmpty(),
        dateTime = scheduledAt.takeIf { it.isNotBlank() }?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd HH:mm")) }.orEmpty(),
        duration = durationSeconds?.let { "약 ${it / 60}분" }.orEmpty(),
        capacity = row.optionalInt("capacity") ?: 0,
        participantCount = row.optionalInt("acceptedCount") ?: 0,
        difficulty = difficultyLabel(row.optionalText("difficulty")),
        description = row.optionalText("body").orEmpty(),
        authorName = row.getAsJsonObject("author")?.optionalText("nickname").orEmpty(),
        isBeginnerWelcome = row.get("beginnerWelcome")?.takeUnless { it.isJsonNull }?.asBoolean ?: (row.optionalText("difficulty") == "BEGINNER"),
        details = MateDetails(
            location = row.optionalText("locationSummary").orEmpty(),
            cost = row.optionalInt("estimatedCostKrw")?.toString() ?: "0",
            meetingPoint = row.optionalText("meetingPoint").orEmpty(),
            visibility = if (row.optionalText("visibility") == "MUTUAL") "맞팔 공개" else "전체 공개",
            sportFields = row.getAsJsonObject("sportFields")?.entrySet()?.associate { it.key to it.value.asString }.orEmpty(),
        ),
        serverId = serverId,
        serverStatus = row.optionalText("status"),
    )
}

private fun difficultyLabel(value: String?) = when (value) {
    "BEGINNER" -> "초보"
    "INTERMEDIATE" -> "중급"
    "ADVANCED" -> "상급"
    else -> "미정"
}

private fun JsonObject.requiredText(name: String): String = optionalText(name) ?: error("$name 값이 없습니다.")
private fun JsonObject.optionalText(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
private fun JsonObject.optionalInt(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.asInt

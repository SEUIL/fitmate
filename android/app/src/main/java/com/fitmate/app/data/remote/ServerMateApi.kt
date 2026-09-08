package com.fitmate.app.data.remote

import com.fitmate.app.data.model.MateDetails
import com.fitmate.app.data.model.MatePost
import com.fitmate.app.data.model.Sport
import com.google.gson.JsonObject
import java.util.UUID

/** 메이트 모집글 서버 응답을 기존 화면 모델에 안전하게 변환한다. */
internal class ServerMateApi(private val api: FitMateApiClient) {
    fun posts(): List<MatePost> = api.items("mate-posts").map(::toMatePost)
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
        dateTime = scheduledAt.replace('T', ' ').take(16),
        duration = durationSeconds?.let { "약 ${it / 60}분" }.orEmpty(),
        capacity = row.optionalInt("capacity") ?: 0,
        participantCount = row.optionalInt("acceptedCount") ?: 0,
        difficulty = difficultyLabel(row.optionalText("difficulty")),
        description = row.optionalText("body").orEmpty(),
        authorName = row.getAsJsonObject("author")?.optionalText("nickname").orEmpty(),
        isBeginnerWelcome = row.optionalText("difficulty") == "BEGINNER",
        details = MateDetails(
            location = row.optionalText("locationSummary").orEmpty(),
            cost = row.optionalInt("estimatedCostKrw")?.toString() ?: "0",
        ),
        serverId = serverId,
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

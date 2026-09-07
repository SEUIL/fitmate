package com.fitmate.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** API v1의 공통 응답(data)을 파싱하는 최소 HTTP client입니다. */
internal class FitMateApiClient(
    private val baseUrl: String,
    private val gson: Gson = Gson()
) {
    fun fetchExercises(sportCode: String = "HEALTH"): List<ApiExercise> {
        require(baseUrl.isNotBlank()) { "연결할 서버 주소가 설정되지 않았습니다." }
        val encodedSport = URLEncoder.encode(sportCode, StandardCharsets.UTF_8.name())
        val response = get("$baseUrl/exercises?sportCode=$encodedSport&size=100")
        val payload = response.getAsJsonObject("data") ?: error("운동 목록 응답에 data가 없습니다.")
        val content = payload.getAsJsonArray("content") ?: error("운동 목록 응답에 content가 없습니다.")
        val type = object : TypeToken<List<ApiExercise>>() {}.type
        return gson.fromJson(content, type)
    }

    fun fetchSports(): List<ApiSport> {
        require(baseUrl.isNotBlank()) { "연결할 서버 주소가 설정되지 않았습니다." }
        val payload = get("$baseUrl/sports").getAsJsonObject("data") ?: error("운동 종목 응답에 data가 없습니다.")
        val type = object : TypeToken<List<ApiSport>>() {}.type
        return gson.fromJson(payload, type)
    }

    fun checkEmailAvailability(email: String): ApiAvailability =
        checkAvailability("auth/email-availability", "email", email)

    fun checkNicknameAvailability(nickname: String): ApiAvailability =
        checkAvailability("auth/nickname-availability", "nickname", nickname)

    private fun checkAvailability(path: String, parameter: String, value: String): ApiAvailability {
        require(baseUrl.isNotBlank()) { "연결할 서버 주소가 설정되지 않았습니다." }
        val encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        val payload = get("$baseUrl/$path?$parameter=$encodedValue").getAsJsonObject("data")
            ?: error("중복 확인 응답에 data가 없습니다.")
        return gson.fromJson(payload, ApiAvailability::class.java)
    }

    private fun get(url: String): JsonObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "서버 응답 $status${if (body.isBlank()) "" else ": $body"}" }
            gson.fromJson(body, JsonObject::class.java) ?: error("빈 응답을 받았습니다.")
        } finally {
            connection.disconnect()
        }
    }

    private companion object { const val REQUEST_TIMEOUT_MS = 10_000 }
}

internal data class ApiSport(val id: String, val code: String, val name: String, val displayOrder: Int)

internal data class ApiAvailability(val value: String, val available: Boolean)

internal data class ApiExercise(
    val id: String,
    val sportCode: String,
    val name: String,
    val bodyPart: String?,
    val equipmentType: String?,
    val referenceUrl: String?
)

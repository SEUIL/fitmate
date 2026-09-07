package com.fitmate.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.reflect.TypeToken
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** API v1의 공통 응답(data)을 파싱하는 최소 HTTP client입니다. */
internal class FitMateApiClient(
    private val baseUrl: String,
    private val gson: Gson = Gson(),
    private val tokenStore: SessionTokenStore? = null
) {
    private var tokens: SessionTokens? = null
    private var tokensLoaded = tokenStore == null
    private fun loadSession() {
        if (!tokensLoaded) {
            tokens = try { tokenStore?.read() } catch (_: Exception) { throw ApiFailure(401, "저장된 로그인 정보를 읽지 못했습니다. 다시 로그인해 주세요.") }
            tokensLoaded = true
        }
    }
    @Synchronized fun hasSession(): Boolean { loadSession(); return tokens != null }

    @Synchronized fun authenticate(path: String, body: Any) {
        val result = request("POST", path, body, authenticated = false).asJsonObject
        val value = gson.fromJson(result.getAsJsonObject("tokens"), SessionTokens::class.java)
        require(!value.accessToken.isNullOrBlank() && !value.refreshToken.isNullOrBlank())
        tokenStore?.write(value)
        tokens = value; tokensLoaded = true
    }

    @Synchronized fun logout(all: Boolean = false) {
        // Refresh first, then send the corresponding refresh token and access token together.
        request("GET", "users/me")
        request("POST", if (all) "auth/logout-all" else "auth/logout",
            if (all) null else mapOf("refreshToken" to requireNotNull(tokens).refreshToken), retryAuth = false)
        clearSession()
    }

    @Synchronized fun clearSession() { tokenStore?.write(null); tokens = null; tokensLoaded = true }

    @Synchronized fun request(method: String, path: String, body: Any? = null,
        authenticated: Boolean = true, retryAuth: Boolean = true): JsonElement {
        require(baseUrl.isNotBlank()) { "연결할 서버 주소가 설정되지 않았습니다." }
        if (authenticated) loadSession()
        if (authenticated && tokens == null) throw ApiFailure(401, "로그인이 필요합니다.")
        try {
            val response = exchange(method, "$baseUrl/$path", body, if (authenticated) tokens?.accessToken else null)
            return response.get("data") ?: JsonNull.INSTANCE
        } catch (error: ApiFailure) {
            if (error.status != 401 || !authenticated || !retryAuth) throw error
            val current = tokens ?: throw error
            val refreshed = try {
                val response = exchange("POST", "$baseUrl/auth/refresh", mapOf("refreshToken" to current.refreshToken), null)
                gson.fromJson(response.getAsJsonObject("data"), SessionTokens::class.java)
            } catch (refreshError: ApiFailure) {
                if (refreshError.status in setOf(400, 401, 403)) clearSession()
                throw refreshError
            }
            tokenStore?.write(refreshed); tokens = refreshed
            return request(method, path, body, authenticated, retryAuth = false)
        }
    }

    fun items(path: String, authenticated: Boolean = true): List<JsonObject> =
        request("GET", path, authenticated = authenticated).asJsonObject.getAsJsonArray("items").map { it.asJsonObject }
    fun fetchExercises(sportCode: String = "HEALTH"): List<ApiExercise> {
        require(baseUrl.isNotBlank()) { "연결할 서버 주소가 설정되지 않았습니다." }
        val encodedSport = URLEncoder.encode(sportCode, StandardCharsets.UTF_8.name())
        val response = get("$baseUrl/exercises?sportCode=$encodedSport&size=100")
        val payload = response.getAsJsonObject("data") ?: error("운동 목록 응답에 data가 없습니다.")
        val content = payload.getAsJsonArray("items") ?: error("운동 목록 응답에 items가 없습니다.")
        val type = object : TypeToken<List<ApiExercise>>() {}.type
        return gson.fromJson(content, type)
    }

    fun fetchSports(): List<ApiSport> {
        require(baseUrl.isNotBlank()) { "연결할 서버 주소가 설정되지 않았습니다." }
        val payload = get("$baseUrl/sports").getAsJsonObject("data") ?: error("운동 종목 응답에 data가 없습니다.")
        val type = object : TypeToken<List<ApiSport>>() {}.type
        return gson.fromJson(payload.getAsJsonArray("items"), type)
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

    private fun get(url: String): JsonObject = exchange("GET", url, null, null)

    private fun exchange(method: String, url: String, payload: Any?, token: String?): JsonObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(gson.toJson(payload).toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { gson.fromJson(body, JsonObject::class.java)
                    .getAsJsonObject("error")?.get("message")?.asString }.getOrNull()
                throw ApiFailure(status, message ?: "서버 요청에 실패했습니다. ($status)")
            }
            if (body.isBlank()) return JsonObject()
            gson.fromJson(body, JsonObject::class.java) ?: error("빈 응답을 받았습니다.")
        } finally {
            connection.disconnect()
        }
    }

    private companion object { const val REQUEST_TIMEOUT_MS = 10_000 }
}

internal class ApiFailure(val status: Int, message: String) : Exception(message)

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

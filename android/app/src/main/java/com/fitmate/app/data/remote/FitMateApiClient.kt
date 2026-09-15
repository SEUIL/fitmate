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
import java.util.UUID

/** API v1의 공통 응답(data)을 파싱하는 최소 HTTP client입니다. */
internal class FitMateApiClient(
    private val baseUrl: String,
    private val gson: Gson = Gson(),
    private val tokenStore: SessionTokenStore? = null
) {
    private var tokens: SessionTokens? = null
    private var rememberedAccount: RememberedAccount? = null
    private var tokensLoaded = tokenStore == null
    private fun loadSession() {
        if (!tokensLoaded) {
            val stored = try { tokenStore?.readState() } catch (_: Exception) { throw ApiFailure(401, "저장된 로그인 정보를 읽지 못했습니다. 다시 로그인해 주세요.") }
            tokens = stored?.tokens
            rememberedAccount = stored?.rememberedAccount
            tokensLoaded = true
        }
    }
    @Synchronized fun hasSession(): Boolean { loadSession(); return tokens != null }
    @Synchronized fun rememberedEmail(): String? { loadSession(); return rememberedAccount?.email }

    private fun saveSession(value: SessionTokens?, remembered: RememberedAccount?) {
        tokenStore?.writeState(StoredSession(value, remembered))
        tokens = value; rememberedAccount = remembered; tokensLoaded = true
    }

    @Synchronized fun authenticate(path: String, body: Any, rememberEmail: String? = null) {
        val result = request("POST", path, body, authenticated = false).asJsonObject
        val value = gson.fromJson(result.getAsJsonObject("tokens"), SessionTokens::class.java)
        require(!value.accessToken.isNullOrBlank() && !value.refreshToken.isNullOrBlank())
        saveSession(value, rememberEmail?.trim()?.takeIf { it.isNotEmpty() }?.let { RememberedAccount(it, value.refreshToken) })
    }

    @Synchronized fun continueRememberedAccount() {
        loadSession()
        val account = rememberedAccount ?: throw ApiFailure(401, "기억한 계정이 없습니다. 다시 로그인해 주세요.")
        val refreshed = try {
            val response = exchange("POST", "$baseUrl/auth/refresh", mapOf("refreshToken" to account.refreshToken), null)
            gson.fromJson(response.getAsJsonObject("data"), SessionTokens::class.java).also {
                require(!it.accessToken.isNullOrBlank() && !it.refreshToken.isNullOrBlank())
            }
        } catch (failure: ApiFailure) {
            if (failure.status in setOf(400, 401, 403)) {
                clearSession()
                throw ApiFailure(401, "기억한 계정의 로그인이 만료되었어요. 비밀번호로 다시 로그인해 주세요.")
            }
            throw failure
        }
        saveSession(refreshed, account.copy(refreshToken = refreshed.refreshToken))
    }

    @Synchronized fun logout(all: Boolean = false) {
        loadSession()
        if (!all && rememberedAccount != null) {
            // Explicit opt-in: leave the account available on this device's login screen.
            clearSession(keepRememberedAccount = true)
            return
        }
        // Refresh first, then send the corresponding refresh token and access token together.
        request("GET", "users/me")
        request("POST", if (all) "auth/logout-all" else "auth/logout",
            if (all) null else mapOf("refreshToken" to requireNotNull(tokens).refreshToken), retryAuth = false)
        clearSession()
    }

    @Synchronized fun clearSession(keepRememberedAccount: Boolean = false) {
        if (keepRememberedAccount) loadSession()
        saveSession(null, if (keepRememberedAccount) rememberedAccount else null)
    }

    @Synchronized fun request(method: String, path: String, body: Any? = null,
        authenticated: Boolean = true, retryAuth: Boolean = true, headers: Map<String, String> = emptyMap()): JsonElement {
        require(headers.keys.all { it in setOf("Idempotency-Key", "X-Admin-Reauth") }) { "지원하지 않는 추가 헤더입니다." }
        require(baseUrl.isNotBlank()) { "연결할 서버 주소가 설정되지 않았습니다." }
        if (authenticated) loadSession()
        if (authenticated && tokens == null) throw ApiFailure(401, "로그인이 필요합니다.")
        try {
            val response = exchange(method, "$baseUrl/$path", body, if (authenticated) tokens?.accessToken else null, headers)
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
            require(!refreshed.accessToken.isNullOrBlank() && !refreshed.refreshToken.isNullOrBlank())
            saveSession(refreshed, rememberedAccount?.copy(refreshToken = refreshed.refreshToken))
            return request(method, path, body, authenticated, retryAuth = false, headers = headers)
        }
    }

    /** Limited credentials are never saved in or refreshed through the normal login session. */
    @Synchronized fun appealRequest(method: String, resource: String, token: String, body: Any? = null, key: String? = null): JsonElement {
        require(resource.substringBefore('?') in setOf("sanctions", "appeals"))
        require(token.isNotBlank() && baseUrl.isNotBlank())
        return exchange(method, "$baseUrl/appeal-access/$resource", body, token,
            key?.let { mapOf("Idempotency-Key" to it) } ?: emptyMap(), "Appeal").get("data") ?: JsonNull.INSTANCE
    }

    fun items(path: String, authenticated: Boolean = true): List<JsonObject> =
        request("GET", path, authenticated = authenticated).asJsonObject.getAsJsonArray("items").map { it.asJsonObject }

    @Synchronized fun uploadImage(purpose: String, fileName: String, contentType: String, bytes: ByteArray): JsonObject {
        require(bytes.isNotEmpty()) { "업로드할 사진이 없습니다." }
        require(bytes.size <= 10 * 1024 * 1024) { "사진은 10MB 이하여야 합니다." }
        loadSession()
        val token = tokens?.accessToken ?: throw ApiFailure(401, "로그인이 필요합니다.")
        return multipart("$baseUrl/media?purpose=$purpose", fileName, contentType, bytes, token).getAsJsonObject("data")
    }

    /** Downloads an authenticated media resource returned by the API (for example, a post image). */
    @Synchronized fun downloadImage(path: String): ByteArray {
        loadSession()
        val token = tokens?.accessToken ?: throw ApiFailure(401, "로그인이 필요합니다.")
        val origin = baseUrl.substringBefore("/api/")
        val url = if (path.startsWith("http://") || path.startsWith("https://")) path else "$origin$path"
        return binaryGet(url, token)
    }
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

    private fun exchange(method: String, url: String, payload: Any?, token: String?, headers: Map<String, String> = emptyMap(), scheme: String = "Bearer"): JsonObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
            token?.let { setRequestProperty("Authorization", "$scheme $it") }
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
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
                val code = runCatching { gson.fromJson(body, JsonObject::class.java)
                    .getAsJsonObject("error")?.get("code")?.asString }.getOrNull()
                throw ApiFailure(status, message ?: "서버 요청에 실패했습니다. ($status)", code)
            }
            if (body.isBlank()) return JsonObject()
            gson.fromJson(body, JsonObject::class.java) ?: error("빈 응답을 받았습니다.")
        } finally {
            connection.disconnect()
        }
    }

    private fun multipart(url: String, fileName: String, contentType: String, bytes: ByteArray, token: String): JsonObject {
        val boundary = "FitMate-${UUID.randomUUID()}"
        val safeFileName = fileName.replace('"', '_')
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = REQUEST_TIMEOUT_MS; readTimeout = REQUEST_TIMEOUT_MS
            doOutput = true; instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        return try {
            connection.outputStream.use { output ->
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"\r\nContent-Type: $contentType\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                output.write(bytes)
                output.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ApiFailure(status, runCatching { gson.fromJson(body, JsonObject::class.java).getAsJsonObject("error")?.get("message")?.asString }.getOrNull() ?: "사진 업로드에 실패했습니다. ($status)")
            gson.fromJson(body, JsonObject::class.java) ?: error("빈 응답을 받았습니다.")
        } finally { connection.disconnect() }
    }

    private fun binaryGet(url: String, token: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = REQUEST_TIMEOUT_MS; readTimeout = REQUEST_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                val message = runCatching { gson.fromJson(body, JsonObject::class.java).getAsJsonObject("error")?.get("message")?.asString }.getOrNull()
                throw ApiFailure(status, message ?: "사진을 불러오지 못했습니다. ($status)")
            }
            connection.inputStream.use { it.readBytes() }
        } finally { connection.disconnect() }
    }

    private companion object { const val REQUEST_TIMEOUT_MS = 10_000 }
}

internal class ApiFailure(val status: Int, message: String, val code: String? = null) : Exception(message)

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

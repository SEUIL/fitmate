package com.fitmate.app.ui

import com.fitmate.app.data.model.Sport
import com.fitmate.app.data.model.UserProfile
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class ServerContractTest {
    @Test fun `custom interest change does not rewrite existing sport proficiency`() {
        val before = UserProfile("runner", "서울", setOf(Sport.RUNNING))
        val patch = profilePatch(before, before.copy(customInterests = setOf("요가")), 2, setOf("HEALTH", "RUNNING"))
        assertFalse(patch.containsKey("sports"))
        assertEquals(listOf(mapOf("name" to "요가")), patch["customSports"])
    }
    @Test fun `nickname-only update does not rewrite time preferences or interests`() {
        val before = UserProfile("before", "서울", setOf(Sport.RUNNING), goals = setOf("운동 습관 만들기"), availableTime = "custom schedule")
        val patch = profilePatch(before, before.copy(nickname = "after"), 7, setOf("HEALTH", "RUNNING"))
        assertEquals(mapOf("version" to 7L, "nickname" to "after"), patch)
    }

    @Test fun `signup preserves running-only and custom interests without inserting HEALTH`() {
        val profile = UserProfile("runner", "서울", setOf(Sport.RUNNING, Sport.GOLF), email = "fixture@example.com", birthDate = "2000.01.01", goals = setOf("운동 습관 만들기"), customInterests = setOf("요가"))
        val payload = signupPayload(profile, "not-a-real-password1", "region-id", listOf("terms-id"), setOf("HEALTH", "RUNNING"))
        assertEquals(listOf(mapOf("sportCode" to "RUNNING", "skillLevel" to "BEGINNER")), payload["sports"])
        assertEquals(setOf(mapOf("name" to "요가"), mapOf("name" to "골프")), (payload["customSports"] as List<*>).toSet())
        assertEquals("2000-01-01", payload["birthDate"])
    }

    @Test fun `catalog parses items envelope and refreshes once before replaying unauthorized request`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var profileCalls = 0
        var refreshCalls = 0
        server.createContext("/api/v1") { exchange ->
            val (status, body) = when (exchange.requestURI.path) {
                "/api/v1/auth/login" -> 200 to """{"data":{"tokens":{"accessToken":"fixture-old","refreshToken":"fixture-refresh"}}}"""
                "/api/v1/auth/refresh" -> { refreshCalls++; 200 to """{"data":{"accessToken":"fixture-new","refreshToken":"fixture-rotated"}}""" }
                "/api/v1/users/me" -> {
                    profileCalls++
                    if (exchange.requestHeaders.getFirst("Authorization") == "Bearer fixture-new") 200 to """{"data":{"id":"fixture"}}"""
                    else 401 to """{"error":{"message":"expired"}}"""
                }
                "/api/v1/sports" -> 200 to """{"data":{"items":[{"id":"1","code":"RUNNING","name":"러닝","displayOrder":2}]}}"""
                else -> 200 to """{"data":{"items":[{"id":"2","sportCode":"HEALTH","name":"스쿼트"}],"hasNext":false}}"""
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = FitMateApiClient("http://127.0.0.1:${server.address.port}/api/v1")
            assertEquals("RUNNING", api.fetchSports().single().code)
            assertEquals("스쿼트", api.fetchExercises().single().name)
            api.authenticate("auth/login", emptyMap<String, String>())
            assertEquals("fixture", api.request("GET", "users/me").asJsonObject.text("id"))
            assertEquals(2, profileCalls); assertEquals(1, refreshCalls)
        } finally { server.stop(0) }
    }

    @Test fun `server workout writes only changed sets and retains returned version`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = mutableListOf<String>()
        server.createContext("/api/v1") { exchange ->
            val response = if (exchange.requestURI.path.endsWith("login")) """{"data":{"tokens":{"accessToken":"fixture","refreshToken":"fixture"}}}"""
                else { bodies += exchange.requestBody.bufferedReader().readText(); """{"data":{"id":"set","setNo":1,"weightKg":30,"repetitions":12,"rpe":7,"memo":"","completed":true,"version":1}}""" }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = FitMateApiClient("http://127.0.0.1:${server.address.port}/api/v1")
            api.authenticate("auth/login", emptyMap<String, String>())
            val session = WorkoutSession("스쿼트", false)
            session.serverState = """{"id":"session","status":"IN_PROGRESS","version":1,"exercises":[{"id":"exercise","exerciseName":"스쿼트","sets":[{"setNo":1,"completed":false,"version":0}]}]}"""
            val binding = ServerWorkout(api, session)
            val upload = WorkoutUpload(listOf(com.fitmate.app.data.model.WorkoutSet("스쿼트", 30.0, 12, 7)), true)
            binding.synchronize(upload); binding.synchronize(upload)
            assertEquals(1, bodies.size)
            assertEquals(0, JsonParser.parseString(bodies.single()).asJsonObject.get("version").asInt)
            assertEquals(1, JsonParser.parseString(session.serverState).asJsonObject.rows("exercises").single().rows("sets").single().get("version").asInt)
        } finally { server.stop(0) }
    }
}

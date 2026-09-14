package com.fitmate.app.ui

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Real server/DB through a local relay; only synthetic account data crosses this test connection. */
class WorkoutRecoveryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lostGenerationAndSaveResponsesRecoverWithoutDuplicates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveWorkoutRecovery") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val remote = FitMateApiClient("http://127.0.0.1:8080/api/v1")
        remote.authenticate("auth/signup", mapOf("email" to "recovery-${UUID.randomUUID()}@example.com",
            "password" to ("T!9a" + UUID.randomUUID()), "nickname" to "복구검증-${UUID.randomUUID().toString().take(8)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to remote.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        val dropGeneration = AtomicBoolean(true)
        val dropSave = AtomicBoolean(true)
        val ids = CopyOnWriteArrayList<String>()
        val restoration = StateRestorationTester(compose)
        MateUiTestServer { method, path, body ->
            val response = remote.request(method, path.removePrefix("/"), if (method == "GET") null else body)
            when {
                method == "POST" && path.endsWith("/plans") -> {
                    ids.add(response.asJsonObject.text("id"))
                    if (dropGeneration.get()) 0 to "connection closed" else 200 to response
                }
                method == "POST" && path.endsWith("/save") && dropSave.get() -> 0 to "connection closed"
                else -> 200 to response
            }
        }.use { relay ->
            val api = relay.api()
            restoration.setContent { FitmateTheme { WorkoutDesignTheme {
                WorkoutPlanFlow(Modifier.safeDrawingPadding(), api, {}, {})
            } } }
            await("주 운동 횟수 · 1~7일")
            compose.onNodeWithText("주 운동 횟수 · 1~7일").performTextReplacement("2")
            compose.onNodeWithText("기본 추천 만들기").performClick()
            compose.waitUntil(30_000) { ids.isNotEmpty() && compose.onNodeWithText("기본 추천 만들기").isEnabled() }
            dropGeneration.set(false)
            restoration.emulateSavedInstanceStateRestore()
            await("주 운동 횟수 · 1~7일")
            compose.onNodeWithText("주 운동 횟수 · 1~7일").assertTextContains("2")
            compose.waitUntil(20_000) { compose.onNodeWithText("기본 추천 만들기").isEnabled() }
            compose.onNodeWithText("기본 추천 만들기").performClick()
            await("나의 추천 결과")
            assertTrue(ids.size >= 2)
            assertEquals(1, ids.distinct().size)
            compose.onNodeWithText("내 루틴에 저장").performClick()
            compose.waitUntil(30_000) { remote.items("routines").size == 1 && compose.onNodeWithText("내 루틴에 저장").isEnabled() }
            dropSave.set(false)
            restoration.emulateSavedInstanceStateRestore()
            await("저장한 루틴으로 운동")
            val routines = remote.items("routines")
            assertEquals(1, routines.size)
            val detail = remote.request("GET", "routines/${routines.single().text("id")}").asJsonObject
            assertEquals(2, detail.getAsJsonObject("recommendation").getAsJsonObject("conditions").recordInt("daysPerWeek"))
            assertEquals("SAVED", remote.request("GET", "workout-recommendations/plans/${ids.first()}").asJsonObject.text("status"))
        }
    }
    private fun SemanticsNodeInteraction.isEnabled() = try { assertIsEnabled(); true } catch (_: AssertionError) { false }
    private fun await(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
}

package com.fitmate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.text
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Opt-in connected-device test. Uses only a freshly created synthetic test account. */
class ServerIntegrationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(label: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(label).onFirst().performScrollTo().performClick()
        Thread.sleep(2_000)
    }

    @Test fun loginRoutineWorkoutRoundTrip() {
        compose.activity.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val fixture = FitMateApiClient(BuildConfig.API_BASE_URL)
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "device-$suffix@example.com"
        val password = UUID.randomUUID().toString() + "9a"
        val nickname = "device-$suffix"
        fixture.authenticate("auth/signup", mapOf("email" to email, "password" to password, "nickname" to nickname,
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to fixture.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        compose.waitUntil(20_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        val emailField = compose.onNode(hasSetTextAction() and hasText("이메일"))
        val passwordField = compose.onNode(hasSetTextAction() and hasText("비밀번호"))
        emailField.performTextReplacement(email)
        assertTrue("Email input initial length=${emailField.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.length}, expected=${email.length}", emailField.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == email)
        Thread.sleep(2_000)
        passwordField.performTextReplacement(password)
        compose.waitForIdle()
        Thread.sleep(2_000)
        assertTrue("Email input after focus change length=${emailField.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.length}, expected=${email.length}", emailField.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == email)
        assertEquals("Masked password length differs", password.length, passwordField.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.length)
        compose.onNodeWithText("로그인").assertIsEnabled().performClick()
        Thread.sleep(2_000)
        try {
            compose.waitUntil(20_000) { compose.onAllNodesWithText("${nickname}님의 오늘").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            val visible = compose.onAllNodes(!hasSetTextAction()).fetchSemanticsNodes().mapNotNull {
                it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { text -> text.text }
            }.joinToString(" | ").replace(password, "[redacted]").replace(email, "[test account]")
            throw AssertionError("Login did not reach home. Visible labels: $visible", failure)
        }
        click("운동 시작")
        click("헬스 시작")
        click("이 루틴으로 운동")
        click("운동 시작")
        click("이 세트 기록")
        click("운동 종료하고 리포트 보기")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("오늘도 완료했어요! ✓").fetchSemanticsNodes().isNotEmpty() }
        val recommended = fixture.request("GET", "routines/recommended?sportCode=HEALTH").asJsonObject
        val firstExercise = recommended.getAsJsonArray("items")[0].asJsonObject.text("exerciseId")
        val previous = fixture.request("GET", "exercises/$firstExercise/previous-record").asJsonObject
        assertTrue(previous.getAsJsonArray("sets").size() >= 1)
        val completed = fixture.request("GET", "workout-sessions/${previous.text("workoutSessionId")}").asJsonObject
        assertEquals("COMPLETED", completed.text("status"))
        // Gradle may uninstall this debug app after instrumentation; this verifies the report before teardown.
    }
}

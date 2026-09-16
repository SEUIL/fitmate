package com.fitmate.app.ui

import android.app.UiAutomation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.model.UserProfile
import com.fitmate.app.ui.theme.*
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Physical landscape display and real IME; synthetic state, no server writes. */
class UiFollowupDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    @Volatile private var keyboardVisible = false

    @After fun restoreRotation() { automation.setRotation(UiAutomation.ROTATION_UNFREEZE) }

    private fun landscape(dark: Boolean, large: Boolean = false, body: @Composable () -> Unit) {
        assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
        compose.waitUntil(8_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            val density = LocalDensity.current
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            SideEffect { keyboardVisible = imeVisible }
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f),
                LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = dark) { WorkoutDesignTheme {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) { body() }
                } }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Rotation and IME transitions run outside Compose's idling resources.
        automation.waitForIdle(1_000, 5_000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-followup").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            checkNotNull(automation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun lightLandscapeLargeHomeActions() = home(false)
    @Test fun darkLandscapeLargeHomeActions() = home(true)
    private fun home(dark: Boolean) {
        val calls = mutableListOf<String>()
        val state = SocialState(emptyList(), emptyList())
        landscape(dark, large = true) {
            SocialHome(Modifier, UserProfile("테스트", "", emptySet()), state, null,
                start = { calls += "start" }, mates = { calls += "mates" }, community = {},
                dm = { calls += "dm" }, inbody = { calls += "inbody" },
                notifications = { calls += "notices" }, history = { calls += "history" })
        }
        capture("home-landscape-large-${if (dark) "dark" else "light"}")
        compose.onNodeWithContentDescription("알림").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("DM 열기").assertIsDisplayed().performClick()
        listOf("운동 시작", "메이트", "운동 기록", "인바디").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(listOf("notices", "dm", "start", "mates", "history", "inbody"), calls) }
    }

    @Test fun lightLandscapeKeyboardRecordsSet() = workout(false)
    @Test fun darkLandscapeKeyboardRecordsSet() = workout(true)
    private fun workout(dark: Boolean) {
        val session = WorkoutSession("스쿼트", false).apply {
            isTimerRunning = false; weightText = "20"; repetitionText = "10"; rpeText = "7"
        }
        landscape(dark) { WorkoutScreen(Modifier, session, emptyList(), emptyList(), null, {}, { true }, {}, {}, {}) }
        compose.onNodeWithText("중량 (kg)").performScrollTo().performClick()
        compose.waitUntil(8_000) { keyboardVisible }
        compose.onNodeWithText("중량 (kg)").performTextReplacement("22.5")
        compose.onNodeWithText("횟수").performScrollTo().assertIsDisplayed().performClick().performTextReplacement("12")
        compose.onNodeWithText("RPE", substring = false).performScrollTo().assertIsDisplayed().performTextReplacement("8")
        capture("workout-landscape-keyboard-${if (dark) "dark" else "light"}")
        compose.onNodeWithText("이 세트 기록").performScrollTo().assertIsDisplayed()
        capture("workout-landscape-record-${if (dark) "dark" else "light"}")
        compose.onNodeWithText("이 세트 기록").performClick()
        compose.runOnIdle {
            assertEquals(1, session.recordedSets.size)
            assertEquals(22.5, session.recordedSets.single().weightKg, .001)
            assertEquals(12, session.recordedSets.single().repetitions)
            assertEquals(8, session.recordedSets.single().rpe)
            session.isRestTimerRunning = false
        }
        Espresso.closeSoftKeyboard()
        compose.waitUntil(8_000) { !keyboardVisible }
        compose.onNodeWithText("전체 운동 보기").performScrollTo().performClick()
        compose.onNode(hasText("전체 운동", substring = false) and !hasAnyAncestor(hasScrollAction())).assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("이 세트 기록").assertIsDisplayed()
    }
}

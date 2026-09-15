package com.fitmate.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LaunchTimingDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun launchRemainsVisibleForTwoSeconds() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Text(if (rememberLaunchReady()) "ready" else "loading") }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("loading").assertExists()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("loading").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("ready").assertExists()
    }
}

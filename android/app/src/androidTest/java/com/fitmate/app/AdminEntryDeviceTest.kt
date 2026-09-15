package com.fitmate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AdminEntryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun loginThenSettingsThenOperatorCenter() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(!args.getString("adminEmail").isNullOrBlank())
        compose.activity.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        if (args.getString("phase") != "restart") {
        compose.waitUntil(20_000) { compose.onAllNodes(hasSetTextAction() and hasText("이메일")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("이메일")).performTextInput(requireNotNull(args.getString("adminEmail")))
        compose.onNode(hasSetTextAction() and hasText("비밀번호")).performTextInput(requireNotNull(args.getString("adminPassword")))
        closeSoftKeyboard()
        compose.onNodeWithText("로그인").performScrollTo().assertIsEnabled().performClick()
        }
        openCenter()
        if (args.getString("phase") == "restart") return
        try {
            compose.activity.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(20_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            openCenter()
        } finally {
            compose.activity.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
        compose.waitUntil(20_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        compose.activityRule.scenario.recreate()
        openCenter()
    }
    private fun openCenter() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("프로필").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("프로필").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("프로필 설정").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("프로필 설정").performClick()
        compose.onNodeWithText("운영자 센터").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("SUPER_ADMIN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SUPER_ADMIN").assertExists()
        compose.onNodeWithText("더보기", useUnmergedTree = true).performClick()
        compose.onNodeWithText("공지 관리").performScrollTo().assertExists()
    }
}

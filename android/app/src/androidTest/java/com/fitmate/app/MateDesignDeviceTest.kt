package com.fitmate.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MateDesignDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filterAppliesOnlyOnConfirmationInDarkMode() {
        var applied: MateFilters? = null
        compose.setContent { FitmateTheme(darkTheme = true) {
            MateFilterPage(Modifier, FitMateApiClient("http://127.0.0.1:8080/api/v1"), MateFilters(), {}, { applied = it })
        } }
        compose.onNodeWithText("오늘").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("선택한 조건 적용").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("오늘", applied?.date) }
    }

    @Test fun filterBackDiscardsDraftInLightMode() {
        var applied: MateFilters? = null
        var wentBack = false
        compose.setContent { FitmateTheme(darkTheme = false) {
            MateFilterPage(Modifier, FitMateApiClient("http://127.0.0.1:8080/api/v1"), MateFilters(), { wentBack = true }, { applied = it })
        } }
        compose.onNodeWithText("내일").performClick()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.runOnIdle { assertTrue(wentBack); assertNull(applied) }
    }
}

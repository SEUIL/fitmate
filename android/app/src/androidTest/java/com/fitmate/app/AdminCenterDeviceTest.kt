package com.fitmate.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Rule
import org.junit.Test

class AdminCenterDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportCanBeReviewedAndSanctionedInDemoOnly() {
        compose.setContent { FitmateTheme { AdminCenter {} } }
        compose.onNodeWithText("최근 신고").performScrollTo()
        compose.onNodeWithText("반복적인 홍보 게시물").performScrollTo().performClick()
        compose.onNodeWithText("검토 시작").performScrollTo().performClick()
        compose.onNodeWithText("반려 / 조치 선택").performScrollTo().performClick()
        compose.onNodeWithText("처리 내용 확인").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("기간 정지").performScrollTo().performClick()
        compose.onNodeWithText("처리 사유 및 사용자 안내 (필수)").performScrollTo().performTextInput("반복적인 광고 게시로 7일간 제한됩니다.")
        compose.onNodeWithText("처리 내용 확인").performScrollTo().performClick()
        compose.onNodeWithText("데모 적용").performClick()
        compose.onNodeWithText("데모 처리 완료").performScrollTo().assertExists()
        compose.onNodeWithText("검토 시작").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.onNodeWithText("오늘의 운영 현황").performScrollTo().assertExists()
    }
}

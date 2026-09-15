package com.fitmate.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred

class AdminCenterDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportCanBeReviewedAndSanctionedInDemoOnly() {
        compose.setContent { FitmateTheme { AdminCenter(AdminUiState(FakeAdminRepository())) {} } }
        awaitText("최근 24시간 운영 현황")
        compose.onNodeWithText("최근 신고").performScrollTo()
        compose.onNodeWithText("반복적인 홍보 게시물").performScrollTo().performClick()
        awaitText("검토 시작")
        compose.onNodeWithText("검토 시작").performScrollTo().performClick()
        awaitText("반려 / 조치 선택")
        compose.onNodeWithText("반려 / 조치 선택").performScrollTo().performClick()
        compose.onNodeWithText("처리 내용 확인").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("기간 정지").performScrollTo().performClick()
        compose.onNodeWithText("처리 사유 및 사용자 안내 (필수)").performScrollTo().performTextInput("반복적인 광고 게시로 7일간 제한됩니다.")
        compose.onNodeWithText("처리 내용 확인").performScrollTo().performClick()
        compose.onNodeWithText("적용").performClick()
        awaitText("처리 결과")
        compose.onNodeWithText("처리 결과").performScrollTo().assertExists()
        compose.onNodeWithText("검토 시작").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.onNodeWithText("최근 24시간 운영 현황").performScrollTo().assertExists()
    }

    @Test fun failedReadShowsRetryInsteadOfDemoCounts() {
        var failRead = true
        val fake = FakeAdminRepository(latencyMs = 0)
        val state = AdminUiState(object : AdminRepository by fake {
            override suspend fun fetch(): AdminSnapshot {
                if (failRead) throw AdminFailure(AdminError.NETWORK)
                return fake.fetch()
            }
        })
        compose.setContent { FitmateTheme { AdminCenter(state) {} } }
        awaitText("최신 상태 다시 조회")
        compose.onNodeWithText("대기 신고").assertDoesNotExist()
        compose.runOnIdle { failRead = false }
        compose.onNodeWithText("최신 상태 다시 조회").performClick()
        awaitText("최근 24시간 운영 현황")
        compose.onNodeWithText("대기 신고").assertExists()
    }

    @Test fun loadingDoesNotDisplaySeededNumbers() {
        val release = CompletableDeferred<Unit>()
        val fake = FakeAdminRepository(latencyMs = 0)
        val state = AdminUiState(object : AdminRepository by fake {
            override suspend fun fetch(): AdminSnapshot { release.await(); return fake.fetch() }
        })
        compose.setContent { FitmateTheme { AdminCenter(state) {} } }
        awaitText("불러오거나 처리하는 중…")
        compose.onNodeWithText("대기 신고").assertDoesNotExist()
        compose.runOnIdle { release.complete(Unit) }
        awaitText("최근 24시간 운영 현황")
    }

    @Test fun conflictRequiresRefreshAndDoesNotShowSuccess() {
        val fake = FakeAdminRepository(latencyMs = 0)
        val state = AdminUiState(object : AdminRepository by fake {
            override suspend fun claim(id: String, version: Long): AdminSnapshot = throw AdminFailure(AdminError.VERSION_CONFLICT)
        })
        compose.setContent { FitmateTheme { AdminCenter(state) {} } }
        awaitText("최근 24시간 운영 현황")
        compose.onNodeWithText("반복적인 홍보 게시물").performScrollTo().performClick()
        awaitText("검토 시작")
        compose.onNodeWithText("검토 시작").performScrollTo().performClick()
        awaitText("최신 상태 다시 조회")
        compose.onNodeWithText("처리 결과").assertDoesNotExist()
        compose.onNodeWithText("검토 시작").assertIsNotEnabled()
        compose.onNodeWithText("최신 상태 다시 조회").performScrollTo().performClick()
        awaitText("최근 24시간 운영 현황")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}

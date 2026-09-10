package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.*
import com.fitmate.app.ui.theme.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in development API check. Only own synthetic MUTUAL posts/comments; no DM or other-user actions. */
class SocialLiveUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightCreateEditCommentAndReload() = verify(false)
    @Test fun darkCreateEditCommentAndReload() = verify(true)
    private fun click(label: String) {
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun verify(dark: Boolean) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSocialUi") == "true")
        check(BuildConfig.APPLICATION_ID.endsWith(".dev.records"))
        val unique = UUID.randomUUID().toString()
        val api = FitMateApiClient(BuildConfig.API_BASE_URL)
        api.authenticate("auth/signup", mapOf("email" to "social-ui-$unique@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "소셜UI-${unique.take(6)}", "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }))
        val account = api.request("GET", "users/me").asJsonObject
        val server = ServerSocialApi(api)
        val state = SocialState(emptyList(), emptyList()).apply { openComposer = true }
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val previous = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = previous } }
            CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {}) {
                FitmateTheme(darkTheme = dark) { WorkoutDesignTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                    CommunityFlow(Modifier, state, account.text("nickname"), emptyList(), emptyList(), server, account.text("id"))
                } } }
            }
        }
        val body = "합성 UI 검증 ${unique.take(6)}"
        compose.onNodeWithText("오늘의 운동이나 생각").performTextInput(body)
        click("맞팔 공개")
        click("게시 저장")
        compose.waitUntil(20_000) { state.composerText.value.isEmpty() && state.feed.any { it.post.content == body } }
        val entry = state.feed.single { it.post.content == body }
        val postId = requireNotNull(state.serverPostId(entry.post.id))
        val created = server.post(postId)
        assertEquals("MUTUAL", created.text("visibility"))
        compose.onNodeWithContentDescription("게시물 더 보기").performClick()
        click("수정")
        compose.onNodeWithText("오늘의 운동이나 생각").performTextReplacement("$body 수정")
        click("게시 저장")
        compose.waitUntil(20_000) { state.composerText.value.isEmpty() && state.feed.any { it.post.content == "$body 수정" } }
        val updated = server.post(postId)
        assertEquals("$body 수정", updated.text("content"))
        assertEquals("MUTUAL", updated.text("visibility"))
        assertTrue(updated.get("version").asLong > created.get("version").asLong)
        compose.onNodeWithContentDescription("댓글 보기").performClick()
        compose.onNodeWithText("댓글 남기기").performTextInput("내 테스트 댓글")
        click("게시")
        compose.waitUntil(20_000) { state.feed.firstOrNull { it.post.id == entry.post.id }?.comments?.any { it.text == "내 테스트 댓글" } == true }
        assertEquals("내 테스트 댓글", server.comments(postId).single().text("content"))
        compose.onNodeWithContentDescription("댓글 닫기").performClick()
        assertTrue(server.feed().any { it.text("postId") == postId })
        assertTrue(server.feed(followingOnly = true).any { it.text("postId") == postId })
        assertTrue(server.conversations().isEmpty())
        assertTrue(server.messageRequests().isEmpty())
    }
}

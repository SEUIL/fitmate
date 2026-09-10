package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.model.CommunityPost
import com.fitmate.app.data.remote.ServerSocialApi
import com.fitmate.app.ui.theme.*
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic state / loopback HTTP only. Never sends to another account. */
class SocialRedesignDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun content(dark: Boolean, large: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val previous = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = previous } }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {},
                LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                FitmateTheme(darkTheme = dark) { WorkoutDesignTheme {
                    Surface(Modifier.widthIn(max = if (large) 320.dp else 600.dp).fillMaxHeight().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) { body() }
                } }
            }
        }
        compose.waitForIdle()
    }

    private fun waitText(value: String) = compose.waitUntil(8_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    private fun click(value: String) {
        waitText(value)
        val node = compose.onNodeWithText(value)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun capture(name: String) {
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "social-ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun social() = SocialState(emptyList(), emptyList()).apply { feed.clear(); chats.clear() }

    @Test fun communityLight() = community(false)
    @Test fun communityDark() = community(true)
    private fun community(dark: Boolean) {
        val state = social().apply {
            feed += FeedEntry(CommunityPost(1, "테스트 민트", "오늘도 운동 완료!", false, 3, 2, "방금 전"), mine = true,
                comments = listOf(FeedComment(2, "테스트 러너", "내일도 함께 힘내요!", false), FeedComment(3, "테스트 민트", "꾸준히 이어가 볼게요.", true)))
        }
        val theme = if (dark) "dark" else "light"
        content(dark) { CommunityFlow(Modifier, state, "테스트 민트", emptyList(), emptyList()) }
        capture("16-feed-$theme")
        compose.onNodeWithContentDescription("좋아요").performClick()
        compose.runOnIdle { assertTrue(state.feed.single().post.isLiked) }
        compose.onNodeWithContentDescription("댓글 보기").performClick()
        waitText("댓글 남기기")
        capture("17-comments-$theme")
        compose.onNodeWithText("댓글 남기기").performTextInput("테스트 댓글")
        click("게시")
        compose.runOnIdle { assertEquals("테스트 댓글", state.feed.single().comments.last().text) }
        compose.onNodeWithContentDescription("댓글 닫기").performClick()
        compose.onNodeWithContentDescription("게시물 작성").performClick()
        compose.onNodeWithText("오늘의 운동이나 생각").performTextInput("새 운동 일지")
        capture("18-compose-$theme")
        click("나중에 쓰기")
        compose.onNodeWithContentDescription("게시물 작성").performClick()
        compose.onNodeWithText("오늘의 운동이나 생각").assertTextContains("새 운동 일지")
        click("맞팔 공개")
        click("게시 저장")
        compose.runOnIdle {
            assertEquals("", state.composerText.value)
            assertEquals("내 게시물", state.communityTab.value)
            assertEquals("맞팔 공개", state.feed.first().visibility)
        }
        compose.onAllNodesWithContentDescription("게시물 더 보기")[0].performClick()
        click("수정")
        waitText("게시물 수정")
        capture("18b-edit-$theme")
        compose.onNodeWithText("오늘의 운동이나 생각").performTextReplacement("수정한 운동 일지")
        Espresso.closeSoftKeyboard()
        click("게시 저장")
        compose.runOnIdle { assertEquals("수정한 운동 일지", state.feed.first().post.content); assertEquals(2, state.feed.size) }
    }

    @Test fun dmLight() = dm(false)
    @Test fun dmDark() = dm(true)
    private fun dm(dark: Boolean) {
        val state = social()
        val active = ChatRoom(1, "테스트 민트", listOf(ChatEntry(1, "오늘 운동은 어땠어요?", false, time = "19:30"), ChatEntry(2, "가볍게 전신 운동을 했어요.", true, read = true, time = "19:31"))).apply { unread = 2; muted = true }
        val request = ChatRoom(2, "테스트 러너", listOf(ChatEntry(3, "함께 운동하고 싶어요.", false)), "요청 대기")
        state.chats.addAll(listOf(active, request))
        var room by mutableStateOf<ChatRoom?>(null)
        val theme = if (dark) "dark" else "light"
        content(dark) {
            SocialDelivery(state)
            room?.let { DmChatFlow(Modifier, state, it, { room = null }) } ?: DmListFlow(Modifier, state, { room = it })
        }
        capture("19-dm-list-$theme")
        click("테스트 민트")
        compose.runOnIdle { assertEquals(0, active.unread) }
        capture("20-dm-chat-$theme")
        compose.onNodeWithText("메시지 입력").performTextInput("내일도 힘내요!")
        click("전송")
        compose.runOnIdle { assertEquals("내일도 힘내요!", active.messages.last().text) }
        compose.onNodeWithContentDescription("뒤로").performClick()
        click("테스트 러너")
        compose.onNodeWithText("메시지 입력").assertDoesNotExist()
        compose.onNodeWithContentDescription("사진 첨부").assertDoesNotExist()
        capture("20b-dm-request-$theme")
        click("수락")
        compose.onNodeWithText("메시지 입력").assertExists()
        compose.onNodeWithContentDescription("대화 옵션").performClick()
        click("차단")
        click("확인")
        compose.onNodeWithText("메시지 입력").assertDoesNotExist()
    }

    @Test fun outgoingRequestStopsAfterFirstTextAndRejectedRequestHasNoComposer() {
        val state = social()
        val room = ChatRoom(1, "테스트 요청", emptyList(), "요청 대기", incoming = false)
        content(false) { DmChatFlow(Modifier, state, room, {}) }
        compose.onNodeWithContentDescription("사진 첨부").assertDoesNotExist()
        compose.onNodeWithText("메시지 입력").performTextInput("첫 요청입니다.")
        click("전송")
        compose.onNodeWithText("메시지 입력").assertDoesNotExist()
        compose.runOnIdle { assertTrue(room.requestSent); assertEquals(1, room.messages.size); room.status = "거절" }
        compose.onNodeWithText("메시지 입력").assertDoesNotExist()
        compose.onNodeWithText("거절된 요청입니다. 추가 메시지를 보낼 수 없어요.").assertExists()
    }

    @Test fun largeTextComposerKeepsSaveAndDraftActionsReachable() {
        val state = social().apply { openComposer = true }
        content(true, large = true) { CommunityFlow(Modifier, state, "테스트", emptyList(), emptyList()) }
        compose.onNodeWithText("오늘의 운동이나 생각").performTextInput("보관할 초안")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("게시 저장").assertIsDisplayed()
        click("초안 삭제")
        click("취소")
        compose.runOnIdle { assertEquals("보관할 초안", state.composerText.value) }
        click("초안 삭제")
        click("확인")
        compose.runOnIdle { assertEquals("", state.composerText.value) }
    }

    @Test fun failedServerSaveKeepsDraftAndRetriesSameVisibility() {
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val writes = CopyOnWriteArrayList<JsonObject>()
        MateUiTestServer { method, path, body ->
            when {
                path.startsWith("/community/posts?") -> 200 to mapOf("items" to emptyList<Any>(), "hasNext" to false)
                path == "/users/me/blocks" -> 200 to mapOf("items" to emptyList<Any>())
                path == "/community/posts" && method == "POST" -> {
                    writes += body
                    if (fail.get()) 503 to "저장 연결 테스트 오류" else 200 to mapOf("postId" to "post", "author" to mapOf("userId" to "me", "nickname" to "테스트"), "content" to body.text("content"), "visibility" to body.text("visibility"), "version" to 1)
                }
                else -> 404 to "Unexpected fixture path"
            }
        }.use { fixture ->
            val state = social().apply { openComposer = true }
            val server = ServerSocialApi(fixture.api())
            content(false) { CommunityFlow(Modifier, state, "테스트", emptyList(), emptyList(), server, "me") }
            compose.onNodeWithText("오늘의 운동이나 생각").performTextInput("서버 저장 테스트")
            Espresso.closeSoftKeyboard()
            click("맞팔 공개")
            click("게시 저장")
            waitText("저장 연결 테스트 오류")
            compose.runOnIdle { assertEquals("서버 저장 테스트", state.composerText.value); fail.set(false) }
            click("게시 저장")
            compose.waitUntil(8_000) { state.composerText.value.isEmpty() }
            assertEquals(2, writes.size)
            assertTrue(writes.all { it.text("content") == "서버 저장 테스트" && it.text("visibility") == "MUTUAL" })
        }
    }

    @Test fun dmListFailureShowsRetryInsteadOfEmptySuccess() {
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        MateUiTestServer { _, path, _ ->
            if (path == "/dm/conversations") {
                if (fail.get()) 503 to "대화 연결 테스트 오류" else 200 to mapOf("items" to emptyList<Any>())
            } else 404 to "Unexpected fixture path"
        }.use { fixture ->
            val server = ServerSocialApi(fixture.api())
            val state = social()
            content(false) { DmListFlow(Modifier, state, {}, server) }
            waitText("대화 연결 테스트 오류")
            compose.onNodeWithText("아직 대화가 없습니다.").assertDoesNotExist()
            fail.set(false)
            click("다시 불러오기")
            waitText("아직 대화가 없습니다.")
            compose.onNodeWithText("대화 연결 테스트 오류").assertDoesNotExist()
        }
    }
}

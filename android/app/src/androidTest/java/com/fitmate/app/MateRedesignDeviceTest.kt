package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.model.*
import com.fitmate.app.ui.theme.*
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MateRedesignDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val postId = "20000000-0000-0000-0000-000000000001"
    private val title = "퇴근 후 가볍게 5 km 함께 달려요"
    private val regionId = "10000000-0000-0000-0000-000000000001"
    private val profile = UserProfile("테스트", "테스트 지역", emptySet(), activityRegionId = regionId)

    private inner class Fixture : AutoCloseable {
        @Volatile var owner = false
        @Volatile var status = "RECRUITING"
        @Volatile var application = ""
        @Volatile var applicantStatus = "PENDING"
        @Volatile var writable = true
        @Volatile var failSend = false
        @Volatile var failList = false
        @Volatile var empty = false
        val sends = CopyOnWriteArrayList<JsonObject>()
        val changes = CopyOnWriteArrayList<JsonObject>()
        val messages = CopyOnWriteArrayList<Map<String, Any>>(listOf(
            mapOf("id" to "m1", "senderId" to "host", "body" to "편한 운동화와 물을 준비해 주세요.", "sequenceNo" to 1),
            mapOf("id" to "m2", "senderId" to "guest", "body" to "네, 내일 뵙겠습니다!", "sequenceNo" to 2)))
        fun post(id: String = postId, name: String = title): Map<String, Any> = buildMap {
            putAll(mapOf("id" to id, "title" to name, "body" to "6분/km로 함께 달려요. 운동화와 물을 준비해 주세요.", "authorId" to "host",
                "author" to mapOf("nickname" to "달리미"), "sportCode" to "RUNNING", "status" to status,
                "scheduledStartAt" to "2026-09-11T10:30:00Z", "durationSeconds" to 3600, "capacity" to 4, "acceptedCount" to 2,
                "locationSummary" to "서울숲 인근", "regionId" to regionId, "difficulty" to "BEGINNER", "estimatedCostKrw" to 0,
                "visibility" to "PUBLIC", "beginnerWelcome" to true, "version" to 7,
                "sportFields" to mapOf("러닝 유형" to "거리 목표", "목표 거리 (km)" to "5", "목표 페이스" to "6분/km", "진행 방식" to "함께 달리기", "코스" to "공원", "GPS 기록" to "사용", "우천 시" to "협의"),
                "allowedActions" to if (!owner && status == "RECRUITING" && application.isBlank()) listOf("APPLY", "OPEN_INQUIRY_ROOM") else emptyList<String>(), "myInquiryRoomId" to "room"))
            if (owner || application == "ACCEPTED") put("meetingPoint", "안내소 앞")
            if (application.isNotBlank()) put("myApplication", mapOf("id" to "application", "status" to application, "version" to 3))
        }
        fun page(items: List<Any>, next: Boolean = false) = mapOf("items" to items, "hasNext" to next, "nextCursor" to if (next) "next" else "")
        val server = MateUiTestServer { method, path, body ->
            when {
                path == "/sports" -> 200 to page(Sport.entries.map { mapOf("id" to it.name, "code" to if (it == Sport.FITNESS) "HEALTH" else it.name, "name" to it.label, "displayOrder" to it.ordinal) })
                path.startsWith("/mate-posts?") -> if (failList) 503 to "목록 연결 테스트 오류" else 200 to page(if (empty) emptyList() else if (path.contains("cursor=next")) listOf(post(), post("20000000-0000-0000-0000-000000000002", "주말 러닝 모임")) else listOf(post()), !empty && !path.contains("cursor=next"))
                path == "/mate-posts/$postId" && method == "PATCH" -> { changes += body; 200 to post() }
                path == "/mate-posts/$postId" -> 200 to post()
                path.contains("/applications?") -> 200 to page(listOf(mapOf("id" to "application", "userId" to "guest", "applicant" to mapOf("nickname" to "러너봄"), "status" to applicantStatus, "version" to 3)))
                path.endsWith("/applications") -> { application = "PENDING"; 200 to emptyMap<String, String>() }
                path.endsWith("/accept") -> { applicantStatus = "ACCEPTED"; changes += body; 200 to emptyMap<String, String>() }
                path.endsWith("/reject") -> { applicantStatus = "REJECTED"; changes += body; 200 to emptyMap<String, String>() }
                path.startsWith("/mate-chat-rooms?") -> 200 to page(listOf(mapOf("id" to "room", "author" to mapOf("userId" to "host", "nickname" to "달리미"), "inquirer" to mapOf("userId" to "guest", "nickname" to "러너봄"), "lastMessage" to mapOf("body" to "편한 운동화와 물을 준비해 주세요."), "unreadCount" to 2)))
                path == "/mate-chat-rooms/room" -> 200 to mapOf("writable" to writable, "author" to mapOf("userId" to "host"), "myLastReadSequence" to 99, "inquirerLastReadSequence" to 2, "hostLastReadSequence" to 2)
                path.contains("/messages?") -> {
                    val after = path.substringAfter("afterSequence=").toLong()
                    200 to page(messages.filter { (it["sequenceNo"] as Int) > after })
                }
                path.endsWith("/messages") -> {
                    sends += body
                    if (failSend) 503 to "전송 연결 테스트 오류" else {
                        val message = mapOf("id" to "m3", "senderId" to "guest", "body" to body.text("body"), "sequenceNo" to 3)
                        if (messages.none { it["id"] == "m3" }) messages += message
                        200 to message
                    }
                }
                else -> 200 to emptyMap<String, String>()
            }
        }
        val api = server.api()
        override fun close() = server.close()
    }

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
    private fun waitText(value: String) {
        try { compose.waitUntil(8_000) { compose.onAllNodesWithText(value, substring = false).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: ComposeTimeoutException) { throw AssertionError("Fixture UI did not show $value: ${compose.onRoot().printToString()}", error) }
    }
    private fun click(value: String) {
        waitText(value)
        val node = compose.onNodeWithText(value, substring = false)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(300)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "mate-ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun scrollHeading(label: String) {
        val heading = compose.onNodeWithText(label).performScrollTo()
        val viewport = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        val distance = heading.fetchSemanticsNode().boundsInRoot.top - viewport.fetchSemanticsNode().boundsInRoot.top - 24f
        viewport.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        compose.waitForIdle()
    }

    @Test fun lightScreensKeepApplicationAndNavigationRules() = reviewScreens(false)
    @Test fun darkScreensKeepApplicationAndNavigationRules() = reviewScreens(true)
    private fun reviewScreens(dark: Boolean): Unit = Fixture().use { fixture ->
        val state = MateServerState()
        val user = mutableStateOf("guest")
        val suffix = if (dark) "dark" else "light"
        content(dark) { key(user.value) { ServerMateFlow(Modifier, fixture.api, state, profile, user.value) {} } }
        waitText(title); screenshot("list-$suffix")
        click("모집 글 더 보기"); waitText("주말 러닝 모임")
        compose.onAllNodesWithText(title).assertCountEquals(1)
        compose.onNodeWithContentDescription("필터").performScrollTo().performClick()
        click("7일 이내"); screenshot("filter-$suffix")
        click("선택한 조건 적용"); waitText(title); click(title)
        waitText("정확한 집결 위치는 참가 확정 후 공개돼요.")
        compose.onNodeWithText("집결 위치 · 안내소 앞").assertDoesNotExist()
        screenshot("detail-$suffix")
        click("참가 신청"); waitText("신청 대기")
        compose.onNodeWithText("참가 취소").assertIsEnabled()
        compose.onNodeWithText("참가 신청").assertDoesNotExist()
        compose.runOnIdle { fixture.owner = true; user.value = "host" }
        waitText("집결 위치 · 안내소 앞")
        scrollHeading("참가 신청 관리")
        screenshot("manage-$suffix")
        click("수락"); waitText("참가 확정")
        compose.onNodeWithText("수락").assertDoesNotExist()
        assertEquals(3, fixture.changes.last().recordInt("version"))
        click("받은 문의 채팅"); waitText("러너봄"); screenshot("inbox-$suffix")
        click("러너봄"); waitText("네, 내일 뵙겠습니다!"); screenshot("chat-$suffix")
        compose.onNodeWithText("메시지", substring = false).assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.runOnIdle { state.page = "작성" }
        waitText("헬스"); screenshot("write-$suffix")
        click("러닝")
        scrollHeading("장소·비용·공개 범위")
        screenshot("write-details-$suffix")
        click("목표 페이스"); click("7분/km")
        compose.onNodeWithText("7분/km").assertExists()
    }

    @Test fun failedMessageRetriesSameKeyAndReadOnlyDisablesInput() = Fixture().use { fixture ->
        fixture.failSend = true
        val state = MateServerState().apply { page = "채팅"; roomId = "room" }
        content(false) { ServerMateFlow(Modifier, fixture.api, state, profile, "guest") {} }
        waitText("네, 내일 뵙겠습니다!")
        compose.onNodeWithText("읽음").assertExists()
        val input = compose.onNode(hasSetTextAction() and hasText("메시지"))
        input.performTextReplacement(" ")
        compose.onNodeWithText("전송").assertIsNotEnabled()
        input.performTextReplacement("가".repeat(2001))
        compose.onNodeWithText("전송").assertIsNotEnabled()
        input.performTextReplacement("테스트 전송")
        click("전송"); waitText("다시 전송")
        input.assertTextContains("테스트 전송")
        fixture.failSend = false
        click("다시 전송"); waitText("테스트 전송")
        compose.waitUntil(5_000) { fixture.sends.size == 2 }
        assertEquals(fixture.sends[0], fixture.sends[1])
        assertTrue(fixture.sends[0].text("clientMessageId").isNotBlank())
        compose.waitUntil(5_000) { compose.onAllNodesWithText("전송", substring = false).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("", input.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        fixture.writable = false
        waitText("종료된 모임·신청 또는 접근 제한으로 읽기만 가능합니다.")
        compose.onNodeWithText("메시지", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("전송").assertIsNotEnabled()
        screenshot("chat-readonly-light")
    }

    @Test fun listFailureCanRefreshAndEmptyDoesNotOfferPagination(): Unit = Fixture().use { fixture ->
        fixture.failList = true
        content(false) { ServerMateFlow(Modifier, fixture.api, remember { MateServerState() }, profile, "guest") {} }
        waitText("목록 연결 테스트 오류")
        compose.onNodeWithText("조건에 맞는 모임이 아직 없어요.").assertDoesNotExist()
        fixture.failList = false; fixture.empty = true
        compose.onNodeWithContentDescription("새로고침").performClick()
        waitText("조건에 맞는 모임이 아직 없어요.")
        compose.onNodeWithText("모집 글 더 보기").assertDoesNotExist()
        compose.onNodeWithText("모임 만들기").assertIsEnabled()
    }

    @Test fun largeTextFilterResetPreservesAllStatusAndFixedApply() = Fixture().use { fixture ->
        var applied: MateFilters? = null
        content(true, large = true) { MateFilterPage(Modifier, fixture.api, MateFilters(sport = "RUNNING", date = "내일"), {}, { applied = it }) }
        click("초기화")
        compose.onNodeWithText("선택한 조건 적용").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(MateFilters(status = "전체"), applied) }
    }

    @Test fun changingSportAfterListFailureClearsStaleError(): Unit = Fixture().use { fixture ->
        fixture.failList = true
        content(false) { ServerMateFlow(Modifier, fixture.api, remember { MateServerState() }, profile, "guest") {} }
        waitText("목록 연결 테스트 오류")
        fixture.failList = false
        click("헬스")
        waitText(title)
        compose.onNodeWithText("목록 연결 테스트 오류").assertDoesNotExist()
    }

    @Test fun closedMeetingKeepsOwnerAndApplicantActionsRestricted(): Unit = Fixture().use { fixture ->
        fixture.status = "CLOSED"
        fixture.owner = true
        val user = mutableStateOf("host")
        val state = MateServerState().apply { page = "상세"; postId = this@MateRedesignDeviceTest.postId }
        content(false) { key(user.value) { ServerMateFlow(Modifier, fixture.api, state, profile, user.value) {} } }
        waitText("참가 신청 관리")
        compose.onNodeWithText("수락").assertDoesNotExist()
        compose.onNodeWithText("거절").assertDoesNotExist()
        compose.onNodeWithText("모집 정보 수정").assertDoesNotExist()
        compose.runOnIdle { fixture.owner = false; user.value = "guest" }
        waitText("신청할 수 없어요")
        compose.onNodeWithText("신청할 수 없어요").assertIsNotEnabled()
        compose.onNodeWithText("집결 위치 · 안내소 앞").assertDoesNotExist()
    }
}

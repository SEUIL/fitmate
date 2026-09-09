package com.fitmate.app.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.*
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

/** Opt-in. Real Android -> HTTP -> MySQL; AI must run with provider=MOCK. Synthetic data only. */
class MateInbodyDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(): FitMateApiClient {
        assumeTrue(InstrumentationRegistry.getArguments().getString("mateInbody") == "mock")
        return FitMateApiClient(BuildConfig.API_BASE_URL).also { api ->
            val unique = UUID.randomUUID().toString()
            api.authenticate("auth/signup", mapOf(
                "email" to "mate-device-$unique@example.com", "password" to UUID.randomUUID().toString(),
                "nickname" to "검증-${unique.take(12)}",
                "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
                "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
                "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }
            ))
        }
    }
    private fun id(api: FitMateApiClient) = api.request("GET", "users/me").asJsonObject.text("id")
    private fun draft(title: String, visibility: String = "전체 공개") = RecruitmentDraft(
        title, Sport.FITNESS, "내일 19:30", "1시간 30분", 3, "초보", "서버 연동 검증용 모집", true,
        MateDetails("공개 장소", "확정자 전용 2층", "1000", visibility, mapOf("운동 부위" to "전신", "보조 여부" to "없음")))
    private val region = "10000000-0000-0000-0000-000000000001"
    private fun click(label: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onAllNodesWithText(label).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun enter(label: String, value: String) =
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)

    @Test fun mateFieldsPermissionsApplicationsChatAndDetailUi() {
        val host = fixture(); val guest = fixture(); val stranger = fixture()
        val api = ServerMateApi(host); val guestApi = ServerMateApi(guest)
        val title = "기기 검증 ${UUID.randomUUID().toString().take(6)}"
        val body = mateCreateBody(draft(title), region)
        val key = UUID.randomUUID().toString()
        val post = api.create(body, key)
        val postId = post.text("id")
        assertEquals(postId, api.create(body, key).text("id"))
        assertFalse(post.has("meetingPoint"))
        assertEquals("확정자 전용 2층", api.detail(postId).text("meetingPoint"))
        assertEquals("", guestApi.detail(postId).text("meetingPoint"))
        assertEquals("전신", guestApi.detail(postId).getAsJsonObject("sportFields").get("운동 부위").asString)
        assertTrue(guestApi.detail(postId).get("beginnerWelcome").asBoolean)
        val roomId = guestApi.openRoom(postId).text("id")
        val messageId = UUID.randomUUID().toString()
        val sent = guestApi.send(roomId, "안녕하세요", messageId)
        assertEquals(sent.text("id"), guestApi.send(roomId, "안녕하세요", messageId).text("id"))
        assertEquals(1, api.messages(roomId, 0).size)
        api.read(roomId, sent.text("id"))
        assertEquals(1, api.room(roomId).get("myLastReadSequence").asInt)
        guestApi.apply(postId)
        val applicant = api.applications(postId).first { it.text("userId") == id(guest) }
        api.decide(postId, applicant.text("id"), "accept", applicant.get("version").asLong)
        assertEquals("확정자 전용 2층", guestApi.detail(postId).text("meetingPoint"))
        assertEquals("", ServerMateApi(stranger).detail(postId).text("meetingPoint"))
        val mine = guestApi.detail(postId).getAsJsonObject("myApplication")
        guestApi.decide(postId, mine.text("id"), "cancel", mine.get("version").asLong)
        assertEquals("", guestApi.detail(postId).text("meetingPoint"))
        val rejectedApi = ServerMateApi(stranger)
        rejectedApi.apply(postId)
        val pending = api.applications(postId).first { it.text("userId") == id(stranger) }
        api.decide(postId, pending.text("id"), "reject", pending.get("version").asLong)
        assertEquals("REJECTED", rejectedApi.detail(postId).getAsJsonObject("myApplication").text("status"))

        val state = MateServerState().apply { this.postId = postId; page = "상세" }
        val hostId = id(host)
        val profile = host.request("GET", "users/me").asJsonObject.toUserProfile()
        compose.setContent { MaterialTheme { ServerMateFlow(Modifier, host, state, profile, hostId) {} } }
        compose.waitUntil(20_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("집결 위치 · 확정자 전용 2층").assertExists()
        click("모집 정보 수정")
        enter("모집 제목", "$title 수정")
        enter("모집 인원", "5")
        click("수정 저장")
        try {
            compose.waitUntil(20_000) { state.page == "상세" && !state.busy }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Mate save did not return to detail: ${compose.onRoot().printToString()}", error)
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("$title 수정").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("$title 수정", api.detail(postId).text("title"))
        assertEquals(5, api.detail(postId).get("capacity").asInt)
        assertEquals(post.get("scheduledStartAt"), api.detail(postId).get("scheduledStartAt"))
        assertEquals(post.get("sportFields"), api.detail(postId).get("sportFields"))
        api.action(postId, "close", api.detail(postId).get("version").asLong)
        assertEquals("CLOSED", api.detail(postId).text("status"))
        api.action(postId, "cancel", api.detail(postId).get("version").asLong)
        assertEquals("CANCELED", api.detail(postId).text("status"))
    }

    @Test fun mutualPostsAreHiddenUntilBothFollowAndBlockedRequestsFail() {
        val host = fixture(); val guest = fixture()
        val api = ServerMateApi(host); val guestApi = ServerMateApi(guest)
        val postId = api.create(mateCreateBody(draft("맞팔 권한 검증", "맞팔 공개"), region), UUID.randomUUID().toString()).text("id")
        assertFalse(guestApi.posts().any { it.serverId == postId })
        try { guestApi.detail(postId); fail("Unrelated user must not see mutual post") }
        catch (e: ApiFailure) { assertEquals(404, e.status) }
        try { guestApi.apply(postId); fail("Unrelated user must not apply") }
        catch (e: ApiFailure) { assertEquals(404, e.status) }
        try { guestApi.openRoom(postId); fail("Unrelated user must not open inquiry") }
        catch (e: ApiFailure) { assertEquals(404, e.status) }
        val hostId = id(host); val guestId = id(guest)
        val follow = guest.request("POST", "users/$hostId/follow").asJsonObject
        if (follow.text("status") == "PENDING") host.request("POST", "users/me/follow-requests/$guestId/accept")
        assertFalse(guestApi.posts().any { it.serverId == postId })
        val reverse = host.request("POST", "users/$guestId/follow").asJsonObject
        if (reverse.text("status") == "PENDING") guest.request("POST", "users/me/follow-requests/$hostId/accept")
        assertEquals(postId, guestApi.detail(postId).text("id"))
        assertTrue(guestApi.posts().any { it.serverId == postId })
        host.request("POST", "users/$guestId/block")
        assertFalse(guestApi.posts().any { it.serverId == postId })
        try { guestApi.apply(postId); fail("Blocked user must not apply") }
        catch (e: ApiFailure) { assertEquals(404, e.status) }
    }

    @Test fun createFormAndInquiryChatUiPersistToServer() {
        val host = fixture(); val guest = fixture()
        val hostId = id(host)
        val profile = host.request("GET", "users/me").asJsonObject.toUserProfile().copy(activityRegionId = region, region = "테스트 지역")
        val state = MateServerState().apply { page = "작성" }
        val activeApi = mutableStateOf(host)
        val activeProfile = mutableStateOf(profile)
        val activeUser = mutableStateOf(hostId)
        val title = "작성 화면 검증 ${UUID.randomUUID().toString().take(6)}"
        compose.setContent { MaterialTheme { ServerMateFlow(Modifier, activeApi.value, state, activeProfile.value, activeUser.value) {} } }
        enter("모집 제목", title)
        click("내일")
        enter("운동 내용과 준비물을 알려주세요", "같이 안전하게 운동해요")
        enter("공개 장소 (예: 성수역 인근)", "운동장")
        enter("확정자에게 공개할 집결 위치", "북문 앞")
        click("모집 게시")
        compose.waitUntil(20_000) { state.page == "상세" && compose.onAllNodesWithText("집결 위치 · 북문 앞").fetchSemanticsNodes().isNotEmpty() }
        val postId = requireNotNull(state.postId)
        assertEquals(title, ServerMateApi(host).detail(postId).text("title"))
        assertEquals(5400, ServerMateApi(host).detail(postId).get("durationSeconds").asInt)
        val guestProfile = guest.request("GET", "users/me").asJsonObject.toUserProfile()
        val guestId = id(guest)
        compose.runOnIdle {
            activeApi.value = guest; activeProfile.value = guestProfile; activeUser.value = guestId
            state.page = "목록"
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        click(title)
        click("문의하기")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("메시지").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(20_000) { compose.onAllNodes(hasSetTextAction() and hasText("메시지") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        enter("메시지", "안녕하세요")
        click("전송")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("안녕하세요").fetchSemanticsNodes().isNotEmpty() }
        val roomId = requireNotNull(state.roomId)
        assertEquals("안녕하세요", ServerMateApi(host).messages(roomId, 0).single().text("body"))
        ServerMateApi(host).send(roomId, "환영합니다", UUID.randomUUID().toString())
        compose.waitUntil(20_000) { compose.onAllNodesWithText("환영합니다").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, ServerMateApi(guest).messages(roomId, 0).size)
    }

    @Test fun inbodyManualUiAndServerMockAnalysisRoundTrip() {
        val api = fixture(); val server = ServerInbodyApi(api)
        val state = InbodyState()
        compose.setContent { MaterialTheme { InbodyFlow(Modifier, state, server) } }
        click("직접 입력")
        enter("측정일 (YYYY.MM.DD)", "2026.09.01")
        enter("체중 (kg)", "71.2")
        enter("골격근량 (kg)", "30.5")
        enter("체지방률 (%)", "21.4")
        click("검수 후 저장")
        compose.waitUntil(20_000) { state.phase == "목록" && state.records.size == 1 }
        assertEquals(71.2, server.records().single().get("weightKg").asDouble, 0.01)
        repeat(3) { index ->
            server.save(mapOf("measuredDate" to LocalDate.of(2026, 9, 2 + index).toString(),
                "weightKg" to 72 + index, "skeletalMuscleMassKg" to 31, "bodyFatPercentage" to 21,
                "optionalValues" to emptyMap<String, Double>()))
        }
        click("기록 새로고침")
        compose.waitUntil(20_000) { state.records.size == 4 }
        compose.onNodeWithText("최근 기록 3개").assertExists()
        compose.onAllNodesWithText("이전 기록 대비 체중 +1.0 kg").onFirst().assertExists()
        val removed = server.records().first().text("id")
        server.delete(removed)
        click("기록 새로고침")
        compose.waitUntil(20_000) { state.records.size == 3 }
        assertFalse(state.records.any { it.id == removed })

        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
        bitmap.recycle()
        val mediaId = api.uploadImage("INBODY", "synthetic-inbody.png", "image/png", bytes).text("mediaId")
        val created = server.createAnalysis(mediaId)
        assertEquals("MOCK", created.text("provider"))
        compose.runOnIdle { state.analysisId = created.text("id"); state.source = "사진 판독"; state.phase = "판독 중" }
        compose.waitUntil(30_000) { state.phase == "검수" }
        assertTrue(state.reviewNotice.contains("실제 AI 분석이 아닙니다"))
        assertTrue(state.reviewNotice.contains("체지방률"))
        enter("측정일 (YYYY.MM.DD)", "2026.09.05")
        enter("체중 (kg)", "70.1")
        enter("골격근량 (kg)", "30.1")
        enter("체지방률 (%)", "20.2")
        click("확인이 필요한 수치를 검수했어요")
        click("검수 후 저장")
        compose.waitUntil(20_000) { state.phase == "목록" && state.records.size == 4 }
        val saved = server.records().first { it.text("analysisId") == created.text("id") }
        assertEquals(70.1, saved.get("weightKg").asDouble, 0.01)
        assertEquals("2026-09-05", saved.text("measuredDate"))
        assertEquals("AI_OCR", saved.text("sourceType"))
    }
}

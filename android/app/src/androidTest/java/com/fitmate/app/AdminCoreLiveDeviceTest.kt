package com.fitmate.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in synthetic accounts only; never point at a production database. */
class AdminCoreLiveDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val args get() = InstrumentationRegistry.getArguments()
    private fun operator(): FitMateApiClient {
        assumeTrue(args.getString("coreTestApproved") == "true")
        return FitMateApiClient(requireNotNull(args.getString("adminUrl"))).also {
            it.authenticate("auth/login", mapOf("email" to args.getString("adminEmail"), "password" to args.getString("adminPassword")))
        }
    }
    private data class Member(val api: FitMateApiClient, val id: String, val email: String, val password: String, val nickname: String)
    private fun member(): Member {
        val email = "core-device-${UUID.randomUUID()}@example.com"; val password = UUID.randomUUID().toString()
        val nickname = "core-${UUID.randomUUID().toString().take(12)}"
        val api = FitMateApiClient(requireNotNull(args.getString("adminUrl")))
        api.authenticate("auth/signup", mapOf("email" to email, "password" to password, "nickname" to nickname,
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")))
        return Member(api, api.request("GET", "users/me").asJsonObject["id"].asString, email, password, nickname)
    }
    @Test fun operatorNavigationAndToolsInLightTheme() = navigation(com.fitmate.app.ui.theme.AppearanceMode.LIGHT)
    @Test fun operatorNavigationAndToolsInDarkTheme() = navigation(com.fitmate.app.ui.theme.AppearanceMode.DARK)
    private fun navigation(mode: com.fitmate.app.ui.theme.AppearanceMode) {
        val api = operator()
        compose.setContent {
            CompositionLocalProvider(com.fitmate.app.ui.theme.LocalAppearancePreference provides com.fitmate.app.ui.theme.AppearancePreference(mode, {})) {
                FitmateTheme { AdminCenter(apiClient = api) {} }
            }
        }
        await("처리할 일을 한눈에")
        screenshot("redesign-home-${mode.name.lowercase()}")
        compose.onNodeWithTag("admin-tab-1").performClick(); await("신고 관리")
        screenshot("redesign-reports-${mode.name.lowercase()}")
        compose.onNodeWithTag("admin-tab-2").performClick(); await("제재 재검토"); await("새로고침")
        awaitContent()
        screenshot("redesign-appeals-${mode.name.lowercase()}")
        compose.onNodeWithTag("admin-tab-3").performClick(); await("공지 관리")
        screenshot("redesign-tools-${mode.name.lowercase()}")
        listOf("공지 관리" to "새 공지 작성", "카테고리 관리" to "운동 종목 순서", "시스템 현황" to "인바디 판독 현황",
            "운영 규칙" to "규칙 추가", "운영자 권한" to "운영팀 관리", "신고 관련 회원" to "신고에 연결된 회원입니다.", "신고 콘텐츠" to "신고된 원문과 처리 내역을 확인하세요.").forEach { (menu, heading) ->
            click(menu); await(heading)
            awaitContent()
            compose.onNodeWithContentDescription("뒤로가기").assertIsEnabled().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("admin-tab-3").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("admin-tab-3").assertIsSelected()
        }
        compose.onNodeWithTag("admin-tab-0").performClick(); await("처리할 일을 한눈에")
    }
    private fun awaitContent() {
        compose.waitUntil(20_000) { compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().isEmpty() }
    }
    @Test fun operatorUiGrantPersistsAndRevokesTheOldSession() {
        val api = operator(); val member = member()
        compose.setContent { FitmateTheme { AdminOperatorScreen(api) {} } }
        await("운영팀 관리")
        compose.onNodeWithText("회원 ID").performTextInput(member.id); closeSoftKeyboard(); click("회원 확인")
        await(member.nickname)
        compose.onNodeWithText("변경 사유 (필수)").performScrollTo().performTextInput("실기기 권한 검증")
        closeSoftKeyboard(); click("권한 변경"); await("테스트 인증")
        compose.onNodeWithText("6자리 테스트 코드").performTextInput(requireNotNull(args.getString("adminCode")))
        closeSoftKeyboard(); click("인증", false); click("권한 변경"); click("변경 적용", false); await("운영팀 관리")
        try {
            assertEquals("MODERATOR", api.request("GET", "admin/operators/candidates/${member.id}").asJsonObject["currentRole"].asString)
            assertTrue(runCatching { member.api.request("GET", "users/me") }.isFailure)
            screenshot("core-operators-live")
        } finally {
            val auth = api.request("POST", "admin/operators/reauth", mapOf("code" to args.getString("adminCode"))).asJsonObject["token"].asString
            val version = api.request("GET", "admin/operators").asJsonObject["version"].asLong
            api.request("PUT", "admin/operators/${member.id}", mapOf("expectedVersion" to version, "role" to "MODERATOR", "enabled" to false, "reason" to "실기기 검증 종료"),
                headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString(), "X-Admin-Reauth" to auth))
        }
    }
    @Test fun appealAcceptanceUiAndApplicantResultRoundTrip() {
        val api = operator(); val member = member()
        val report = api.request("POST", "reports", mapOf("targetType" to "USER", "targetId" to member.id, "reason" to "SPAM", "detail" to "Core device verification")).asJsonObject["reportId"].asString
        val claimed = api.request("POST", "admin/reports/$report/claim", mapOf("expectedVersion" to 0)).asJsonObject
        api.request("POST", "admin/reports/$report/decisions", mapOf("expectedVersion" to claimed.getAsJsonObject("report")["version"].asLong,
            "resolution" to "RESOLVED", "action" to "WARNING", "reasonCode" to "SPAM", "publicReason" to "실기기 확인용 경고", "internalNote" to "Synthetic fixture"),
            headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
        val token = member.api.request("POST", "auth/appeal-sessions", mapOf("email" to member.email, "password" to member.password), authenticated = false).asJsonObject["token"].asString
        val sanction = member.api.appealRequest("GET", "sanctions", token).asJsonObject.rows("items").single()
        member.api.appealRequest("POST", "appeals", token, mapOf("sanctionId" to sanction.text("id"), "statement" to "실기기 재검토 요청"), UUID.randomUUID().toString())
        assertTrue(api.request("GET", "admin/appeals?status=RECEIVED").asJsonObject.rows("items").any { it.text("nickname") == member.nickname })
        compose.setContent { FitmateTheme { AdminAppealScreen(api) {} } }
        await(member.nickname)
        click(member.nickname)
        click("검토 시작")
        await("검토 중")
        click("수용")
        compose.onNodeWithText("신청자에게 보여줄 사유").performScrollTo().performTextInput("검증 완료로 경고 해제")
        closeSoftKeyboard(); click("검토 결과 저장"); click("처리", false); await("수용 · 제재 해제")
        assertEquals("ACCEPTED", member.api.appealRequest("GET", "appeals", token).asJsonObject.rows("items").single().text("status"))
        screenshot("core-appeal-admin-live")
    }
    @Test fun applicantUiSubmitsAndReadsThePublicDecision() {
        val api = operator(); val member = member()
        val report = api.request("POST", "reports", mapOf("targetType" to "USER", "targetId" to member.id, "reason" to "SPAM", "detail" to "Applicant device verification")).asJsonObject["reportId"].asString
        val claimed = api.request("POST", "admin/reports/$report/claim", mapOf("expectedVersion" to 0)).asJsonObject
        api.request("POST", "admin/reports/$report/decisions", mapOf("expectedVersion" to claimed.getAsJsonObject("report")["version"].asLong,
            "resolution" to "RESOLVED", "action" to "WARNING", "reasonCode" to "SPAM", "publicReason" to "신청 화면 확인용 경고", "internalNote" to "Synthetic fixture"),
            headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
        compose.setContent { FitmateTheme { AppealAccessScreen(member.api) {} } }
        compose.onNodeWithText("이메일").performTextInput(member.email)
        compose.onNodeWithText("비밀번호").performTextInput(member.password)
        closeSoftKeyboard(); click("계정 확인"); await("이의신청"); click("이의신청")
        compose.onNodeWithText("신청 내용").performScrollTo().performTextInput("실기기 사용자 접수 확인")
        closeSoftKeyboard(); click("이의신청 접수")
        await("이의신청이 접수되었습니다. 신청 내역에서 결과를 확인할 수 있어요.")
        val token = member.api.request("POST", "auth/appeal-sessions", mapOf("email" to member.email, "password" to member.password), authenticated = false).asJsonObject["token"].asString
        val appeal = member.api.appealRequest("GET", "appeals", token).asJsonObject.rows("items").single()
        val reviewing = api.request("POST", "admin/appeals/${appeal.text("id")}/claim", mapOf("expectedVersion" to appeal["version"].asLong)).asJsonObject
        api.request("POST", "admin/appeals/${appeal.text("id")}/decision", mapOf("expectedVersion" to reviewing["version"].asLong,
            "status" to "ACCEPTED", "publicReason" to "접수와 결과 확인 완료", "internalNote" to "PRIVATE_DEVICE_NOTE"),
            headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
        click("신청 내역"); await("처리 결과: 접수와 결과 확인 완료")
        compose.onNodeWithText("수용 · 제재 해제").assertExists()
        compose.onNodeWithText("PRIVATE_DEVICE_NOTE", substring = true).assertDoesNotExist()
        assertEquals(member.id, member.api.request("GET", "users/me").asJsonObject["id"].asString)
        screenshot("core-appeal-user-live")
    }
    @Test fun ruleUiSaveAndLivePublicContentReview() {
        val api = operator(); val member = member(); val term = "core-${UUID.randomUUID()}"
        val name = "실기기 규칙 ${UUID.randomUUID().toString().take(6)}"
        compose.setContent { FitmateTheme { AdminRuleScreen(api) {} } }
        await("규칙 추가"); click("규칙 추가")
        compose.onNodeWithText("규칙 이름").performTextInput(name)
        compose.onNodeWithText("탐지할 단어·문구").performScrollTo().performTextInput(term)
        compose.onNodeWithText("변경 사유").performScrollTo().performTextInput("실기기 규칙 저장 검증")
        closeSoftKeyboard(); click("규칙 저장"); await("규칙 추가")
        val rule = api.request("GET", "admin/rules").asJsonObject.rows("items").single { it.text("name") == name }
        fun toggle(enabled: Boolean) = api.request("PUT", "admin/rules/${rule.text("id")}", mapOf("expectedVersion" to rule["version"].asLong,
            "name" to name, "kind" to "WORD", "scope" to "POST", "term" to term, "thresholdCount" to 3, "windowSeconds" to 60,
            "enabled" to enabled, "reason" to "실기기 합성 콘텐츠 검사"), headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString())).asJsonObject
        assertFalse(rule["enabled"].asBoolean)
        val active = toggle(true); rule.addProperty("version", active["version"].asLong)
        try {
            member.api.request("POST", "community/posts", mapOf("content" to "검사 본문 $term", "visibility" to "PUBLIC", "mediaIds" to emptyList<String>()))
            click("검토 후보"); await(name); click(name); await("검사 본문 $term")
            compose.onNodeWithText("검토 메모").performScrollTo().performTextInput("실기기 원문 확인 완료")
            closeSoftKeyboard(); click("검토 완료"); await("검토 완료: 실기기 원문 확인 완료")
            assertTrue(api.request("GET", "admin/rules/detections?reviewed=true").asJsonObject.rows("items").any { it.text("ruleName") == name })
            screenshot("core-rule-review-live")
        } finally { toggle(false) }
    }
    private fun await(text: String) {
        try { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: ComposeTimeoutException) { screenshot("core-live-failure"); throw failure }
    }
    private fun click(text: String, scroll: Boolean = true) {
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithText(text); if (scroll) node.performScrollTo(); node.assertIsEnabled().performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val dir = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("admin-evidence"))
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}

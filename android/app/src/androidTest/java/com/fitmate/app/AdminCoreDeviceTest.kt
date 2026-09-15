package com.fitmate.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.ui.theme.FitmateTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Device UI + in-device HTTP fixtures. Real DB behavior is covered by backend integration tests. */
class AdminCoreDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val time = "2026-09-15T00:00:00Z"
    private fun sanction() = mapOf("id" to "sanction", "action" to "SUSPEND", "reason" to "원래 제재 사유", "startsAt" to time, "endsAt" to time,
        "revokedAt" to null, "canAppeal" to true, "latestAppeal" to null)
    private fun appeal(status: String = "REVIEWING") = mapOf("id" to "appeal", "sanctionId" to "sanction", "userId" to "member", "nickname" to "신청회원",
        "statement" to "검토 요청 내용", "status" to status, "version" to 1, "createdAt" to time, "publicReason" to null,
        "reviewedAt" to null, "internalNote" to null, "canReview" to true, "sanction" to sanction())

    @Test fun limitedAccountCanSubmitAndReadResultsWithoutReplacingMainSession() {
        var submitted = false
        MateUiTestServer { method, path, body ->
            when {
                path == "/auth/appeal-sessions" -> 200 to mapOf("token" to UUID.randomUUID().toString())
                path == "/appeal-access/sanctions" -> 200 to mapOf("items" to listOf(sanction()), "nextCursor" to null)
                path == "/appeal-access/appeals" && method == "POST" -> { assertEquals("새 신청 내용", body["statement"].asString); submitted = true; 200 to appeal("RECEIVED") }
                path == "/appeal-access/appeals" -> 200 to mapOf("items" to listOf(appeal("RECEIVED")), "nextCursor" to null)
                else -> 404 to "Not found"
            }
        }.use { server ->
            val api = server.api()
            compose.setContent { FitmateTheme { AppealAccessScreen(api) {} } }
            compose.onNodeWithText("이메일").performTextInput("fixture@example.com")
            compose.onNodeWithText("비밀번호").performTextInput("fixture-password")
            closeSoftKeyboard(); click("계정 확인"); await("원래 제재 사유")
            click("이의신청"); compose.onNodeWithText("신청 내용").performTextInput("새 신청 내용")
            closeSoftKeyboard(); click("이의신청 접수"); await("이의신청이 접수되었습니다. 신청 내역에서 결과를 확인할 수 있어요.")
            assertTrue(submitted); assertTrue(api.hasSession())
            click("신청 내역"); await("접수됨")
            compose.onNodeWithText("검토 요청 내용").assertExists()
        }
    }
    @Test fun lostDecisionResponseKeepsInputsLockedAndReusesTheSameCommand() {
        val keys = mutableListOf<String>()
        var drop = true
        lateinit var server: MateUiTestServer
        server = MateUiTestServer { _, path, body ->
            when {
                path.startsWith("/admin/appeals?") -> 200 to mapOf("items" to listOf(appeal()), "nextCursor" to null)
                path == "/admin/appeals/appeal" -> 200 to appeal()
                path.endsWith("/decision") -> {
                    keys += requireNotNull(server.requestHeaders["idempotency-key"])
                    assertEquals("검토 결과 안내", body["publicReason"].asString)
                    if (drop) 0 to emptyMap<String, String>() else 200 to appeal("REJECTED")
                }
                else -> 404 to "Not found"
            }
        }
        server.use {
            val api = server.api()
            compose.setContent { FitmateTheme { AdminAppealScreen(api) {} } }
            await("신청회원"); click("신청회원"); await("원래 제재 사유")
            compose.onNodeWithText("신청자에게 보여줄 사유").performScrollTo().performTextInput("검토 결과 안내")
            closeSoftKeyboard(); click("검토 결과 저장"); click("처리", false)
            await("처리 결과 확인")
            compose.onNodeWithText("신청자에게 보여줄 사유").assertIsNotEnabled()
            drop = false; click("처리 결과 확인"); await("기각 · 제재 유지")
            assertTrue(keys.size >= 2); assertEquals(1, keys.toSet().size)
        }
    }
    @Test fun rulePreviewIsSeparateFromSaveAndDefaultsToDisabled() {
        var previews = 0; var saves = 0
        MateUiTestServer { method, path, body ->
            when {
                path == "/admin/rules/preview" -> { previews++; 200 to mapOf("matched" to true, "explanation" to "샘플만 검사했습니다.") }
                method == "PUT" -> { saves++; assertFalse(body["enabled"].asBoolean); 200 to emptyMap<String, String>() }
                path.startsWith("/admin/rules?") -> 200 to mapOf("items" to emptyList<Any>(), "nextCursor" to null)
                else -> 404 to "Not found"
            }
        }.use { server ->
            val api = server.api()
            compose.setContent { FitmateTheme { AdminRuleScreen(api) {} } }
            await("규칙 추가"); click("규칙 추가")
            compose.onNodeWithText("규칙 이름").performTextInput("테스트 규칙")
            compose.onNodeWithText("탐지할 단어·문구").performScrollTo().performTextInput("sample")
            compose.onNodeWithText("변경 사유").performScrollTo().performTextInput("개발 검증")
            compose.onNodeWithText("가상 샘플 (저장되지 않음)").performScrollTo().performTextInput("sample content")
            closeSoftKeyboard(); click("샘플 검사"); await("탐지됨 · 샘플만 검사했습니다.")
            assertEquals(1, previews); assertEquals(0, saves)
            click("규칙 저장"); await("규칙 추가"); assertEquals(1, saves)
        }
    }
    @Test fun operatorChangeUsesTestCodeAndShowsFinalConfirmation() {
        val target = UUID.randomUUID().toString(); var changed = false
        val item = mapOf("userId" to target, "nickname" to "대상회원", "role" to "MODERATOR", "enabled" to true, "accountStatus" to "ACTIVE")
        MateUiTestServer { method, path, body ->
            when {
                path == "/admin/operators/reauth" -> { assertEquals("123456", body["code"].asString); 200 to mapOf("token" to UUID.randomUUID().toString()) }
                method == "PUT" -> { changed = true; 200 to mapOf("operator" to item) }
                path == "/admin/operators" -> 200 to mapOf("version" to 0, "actorId" to "other", "items" to listOf(item), "nextCursor" to null)
                else -> 404 to "Not found"
            }
        }.use { server ->
            val api = server.api()
            compose.setContent { FitmateTheme { AdminOperatorScreen(api) {} } }
            await("대상회원"); click("대상회원"); click("관리자")
            compose.onNodeWithText("변경 사유 (필수)").performScrollTo().performTextInput("운영 담당 변경")
            closeSoftKeyboard(); click("권한 변경"); await("테스트 인증")
            compose.onNodeWithText("6자리 테스트 코드").performTextInput("123456")
            closeSoftKeyboard(); click("인증", false); await("권한 변경")
            assertFalse(changed); click("권한 변경"); await("권한을 변경할까요?")
            assertFalse(changed); click("변경 적용", false); await("운영팀 관리"); assertTrue(changed)
        }
    }
    private fun await(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String, scroll: Boolean = true) {
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithText(text); if (scroll) node.performScrollTo(); node.assertIsEnabled().performClick()
    }
}

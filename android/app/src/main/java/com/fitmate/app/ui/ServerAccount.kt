package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.Sport
import com.fitmate.app.data.model.UserProfile
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
internal fun JsonObject.rows(key: String): List<JsonObject> = getAsJsonArray(key)?.map { it.asJsonObject }.orEmpty()
internal val goalCodes = linkedMapOf("다이어트" to "DIET", "체중 감량" to "DIET", "근육 증가" to "MUSCLE_GAIN", "체력 향상" to "FITNESS", "기록 향상" to "RECORD_IMPROVEMENT", "운동 습관 만들기" to "HABIT", "습관 만들기" to "HABIT")
internal val skillCodes = linkedMapOf("입문" to "BEGINNER", "처음이에요" to "BEGINNER", "6개월 미만" to "UNDER_6_MONTHS", "가끔 해요" to "UNDER_6_MONTHS", "6개월~1년" to "SIX_MONTHS_TO_ONE_YEAR", "1년 이상" to "ONE_TO_THREE_YEARS", "꾸준히 해요" to "ONE_TO_THREE_YEARS", "3년 이상" to "OVER_THREE_YEARS")
internal fun Sport.apiCode() = if (this == Sport.FITNESS) "HEALTH" else name

@Composable internal fun ServerRegionPicker(api: FitMateApiClient, label: String, choose: (String, String) -> Unit) {
    KakaoRegionPicker(api, label, choose = choose)
}

/** 서버에 등록된 시설을 지역 기준으로 찾아 제안하되, 등록되지 않은 시설명 직접 입력도 보존한다. */
@Composable internal fun ServerFacilityPicker(api: FitMateApiClient, regionId: String, value: String, change: (String) -> Unit) {
    var suggestions by remember { mutableStateOf(emptyList<JsonObject>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val query = value.trim()
    LaunchedEffect(regionId, query) {
        suggestions = emptyList(); error = null
        if (regionId.isBlank() || query.length < 2) return@LaunchedEffect
        delay(300)
        loading = true
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            suggestions = withContext(Dispatchers.IO) { api.items("facilities?regionId=$regionId&name=$encoded", false).take(5) }
        } catch (failure: Exception) { error = failure.message ?: "시설을 불러오지 못했습니다." }
        finally { loading = false }
    }
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text("이용 헬스장 (선택)") }, singleLine = true,
        supportingText = { Text(if (regionId.isBlank()) "활동 지역을 먼저 읍/면/동까지 선택하세요." else "등록된 시설을 검색하거나 직접 입력할 수 있어요.") })
    if (loading) Text("시설 검색 중…", style = MaterialTheme.typography.bodySmall)
    suggestions.forEach { facility ->
        TextButton({ change(facility.text("name")) }, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(facility.text("name"))
                facility.text("addressSummary").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    error?.let { Text("시설 추천을 불러오지 못했어요. 직접 입력은 가능합니다.", style = MaterialTheme.typography.bodySmall) }
}

internal fun signupPayload(profile: UserProfile, password: String, regionId: String, terms: List<String>, activeCodes: Set<String>): Map<String, Any> {
    val managed = profile.interests.filter { it.apiCode() in activeCodes }
    val custom = profile.customInterests + profile.interests.filter { it.apiCode() !in activeCodes }.map { it.label }
    val goalList = profile.goals.mapIndexed { index, goal -> mapOf("goalCode" to requireNotNull(goalCodes[goal]), "primary" to (index == 0)) }
    val weekdays = if (profile.availableTime == "주말") listOf(5, 6) else (0..4).toList()
    val start = if (profile.availableTime == "평일 아침") "06:00:00" else if (profile.availableTime == "주말") "09:00:00" else "18:00:00"
    val end = if (profile.availableTime == "평일 아침") "09:00:00" else if (profile.availableTime == "주말") "18:00:00" else "22:00:00"
    return mapOf("email" to profile.email, "password" to password, "nickname" to profile.nickname.trim(), "displayName" to profile.displayName.trim(),
        "birthDate" to java.time.LocalDate.parse(profile.birthDate.replace('.', '-')).toString(),
        "gender" to when (profile.gender) { "남성" -> "MALE"; "여성" -> "FEMALE"; else -> "NONE" },
        "bio" to profile.bio, "activityRegionIds" to listOf(regionId), "facilityNames" to listOf(profile.facilityName.trim()).filter { it.isNotEmpty() },
        "sports" to managed.map { mapOf("sportCode" to it.apiCode(), "skillLevel" to requireNotNull(skillCodes[profile.experience])) },
        "customSports" to custom.map { mapOf("name" to it) }, "goals" to goalList, "consentDocumentIds" to terms,
        "availableTimes" to weekdays.map { mapOf("weekdayLocal" to it, "startLocalTime" to start, "endLocalTime" to end) })
}

internal fun JsonObject.toUserProfile(): UserProfile {
    val p = getAsJsonObject("profile")
    val health = rows("sports").firstOrNull { it.text("sportCode") == "HEALTH" } ?: rows("sports").firstOrNull()
    return UserProfile(nickname = p.text("nickname"), region = rows("activityRegions").joinToString { it.text("name") },
        interests = rows("sports").mapNotNull { selection -> Sport.entries.firstOrNull { it.apiCode() == selection.text("sportCode") } }.toSet(),
        isProfileComplete = true, isPrivate = p.text("visibility") == "PRIVATE", displayName = p.text("displayName"),
        email = text("email"), birthDate = p.text("birthDate"), gender = when (p.text("gender")) { "MALE" -> "남성"; "FEMALE" -> "여성"; else -> "선택 안 함" },
        facilityName = rows("facilities").joinToString { it.text("name") }, experience = skillCodes.entries.firstOrNull { it.value == health?.text("skillLevel") }?.key ?: "입문",
        customInterests = rows("customSports").map { it.text("name") }.toSet(), goals = rows("goals").sortedByDescending { it.get("primary").asBoolean }.mapNotNull { g -> goalCodes.entries.firstOrNull { it.value == g.text("goalCode") }?.key }.toSet(),
        availableTime = rows("availableTimes").joinToString { "${listOf("월", "화", "수", "목", "금", "토", "일").getOrNull(it.text("weekdayLocal").toIntOrNull() ?: -1).orEmpty()}요일 ${it.text("startLocalTime")}~${it.text("endLocalTime")}" }, bio = p.text("bio"), profileImageUrl = p.text("profileImageUrl").ifBlank { null },
        activityRegionId = rows("activityRegions").singleOrNull()?.text("id").orEmpty())
}

internal fun profilePatch(before: UserProfile, after: UserProfile, version: Long, activeCodes: Set<String>): Map<String, Any> {
    val body = mutableMapOf<String, Any>("version" to version)
    if (before.nickname != after.nickname) body["nickname"] = after.nickname.trim()
    if (before.displayName != after.displayName) body["displayName"] = after.displayName.trim()
    if (before.bio != after.bio) body["bio"] = after.bio
    if (before.isPrivate != after.isPrivate) body["visibility"] = if (after.isPrivate) "PRIVATE" else "PUBLIC"
    if (before.activityRegionId != after.activityRegionId) {
        require(after.activityRegionId.isNotBlank()) { "활동 지역을 읍/면/동까지 선택하세요." }
        body["activityRegionIds"] = listOf(after.activityRegionId)
        body["facilityNames"] = after.facilityName.split(',').map { it.trim() }.filter { it.isNotBlank() }
    } else if (before.facilityName != after.facilityName) body["facilityNames"] = after.facilityName.split(',').map { it.trim() }.filter { it.isNotBlank() }
    if (before.interests != after.interests || before.experience != after.experience) {
        body["sports"] = after.interests.filter { it.apiCode() in activeCodes }.map { mapOf("sportCode" to it.apiCode(), "skillLevel" to requireNotNull(skillCodes[after.experience])) }
    }
    if (before.interests != after.interests || before.customInterests != after.customInterests) {
        body["customSports"] = (after.customInterests + after.interests.filter { it.apiCode() !in activeCodes }.map { it.label }).map { mapOf("name" to it) }
    }
    if (before.goals.toList() != after.goals.toList()) body["goals"] = after.goals.mapIndexed { i, goal -> mapOf("goalCode" to requireNotNull(goalCodes[goal]), "primary" to (i == 0)) }
    if (before.availableTime != after.availableTime) {
        val days = if (after.availableTime == "주말") listOf(5, 6) else (0..4).toList()
        val start = if (after.availableTime == "평일 아침") "06:00:00" else if (after.availableTime == "주말") "09:00:00" else "18:00:00"
        val end = if (after.availableTime == "평일 아침") "09:00:00" else if (after.availableTime == "주말") "18:00:00" else "22:00:00"
        body["availableTimes"] = days.map { mapOf("weekdayLocal" to it, "startLocalTime" to start, "endLocalTime" to end) }
    }
    return body
}

@Composable internal fun ServerAccountSettings(modifier: Modifier, api: FitMateApiClient, finished: () -> Unit,
    external: () -> Unit, health: Boolean, setHealth: (Boolean) -> Unit, location: Boolean, setLocation: (Boolean) -> Unit,
    deleteLocalRecords: () -> Unit) = Screen(modifier, background = MaterialTheme.colorScheme.background) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<JsonObject?>(null) }
    var terms by remember { mutableStateOf(emptyList<JsonObject>()) }
    var consents by remember { mutableStateOf(emptyList<JsonObject>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var confirmLocalDeletion by remember { mutableStateOf(false) }
    fun runAction(work: () -> Unit, done: () -> Unit = {}) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try { withContext(Dispatchers.IO) { work() }; done() }
            catch (failure: Exception) { message = failure.message }
            finally { busy = false }
        }
    }
    LaunchedEffect(reload) {
        message = null
        try {
            val loaded = withContext(Dispatchers.IO) { Triple(api.request("GET", "users/me/notification-settings").asJsonObject,
                api.items("terms", false), api.request("GET", "users/me/consents").asJsonArray.map { it.asJsonObject }) }
            settings = loaded.first; terms = loaded.second; consents = loaded.third
        } catch (failure: Exception) { message = failure.message }
    }
    message?.let { ErrorText(it); TextButton({ reload++ }, enabled = !busy) { Text("다시 불러오기") } }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    WorkoutCard {
    Text("계정 및 알림 설정", style = MaterialTheme.typography.titleMedium)
    settings?.let { value ->
        listOf("inAppEnabled" to "앱 알림", "dmEnabled" to "DM 알림", "mateEnabled" to "메이트 알림", "communityEnabled" to "커뮤니티 알림").forEach { (field, label) ->
                AuthToggle(label, value.get(field).asBoolean, { enabled ->
                    val body = value.deepCopy().apply { addProperty(field, enabled) }
                    runAction({ api.request("PATCH", "users/me/notification-settings", body) }, { reload++ })
                }, enabled = !busy)
        }
    }
    }
    WorkoutCard {
    Text("약관 동의", style = MaterialTheme.typography.titleMedium)
    terms.forEach { term ->
        val active = consents.any { it.text("termsDocumentId") == term.text("id") && it.text("withdrawnAt").isBlank() }
        val label = when (term.text("type")) { "SERVICE_TERMS" -> "이용약관"; "PRIVACY" -> "개인정보 처리방침"; else -> term.text("type") }
        AuthToggle("$label · ${term.text("version")}", active,
            { enabled -> runAction({ api.request(if (enabled) "PUT" else "DELETE", "users/me/consents/${term.text("id")}") }, { reload++ }) },
            enabled = !busy && !term.get("required").asBoolean,
            description = if (term.get("required").asBoolean) "서비스 이용에 필요한 필수 동의입니다." else "선택 동의입니다.")
    }
    }
    WorkoutCard {
    Text("기기 내 동의", style = MaterialTheme.typography.titleMedium)
    AuthToggle("기기 내 건강정보 이용 동의", health, setHealth)
    AuthToggle("기기 내 위치정보 이용 동의", location, setLocation)
    TextButton(external) { Text("Samsung Health 연결·권한 관리") }
    }
    WorkoutCard {
    Text("계정 관리", style = MaterialTheme.typography.titleMedium)
    TextButton({ confirmLocalDeletion = true }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("내 기기의 헬스 기록·임시 운동 삭제") }
    if (confirmLocalDeletion) ConfirmAction("로컬 운동 기록 삭제", "이 계정의 기기 내 헬스 기록과 임시 운동만 삭제합니다. 서버 기록은 삭제되지 않습니다. 서버 운동이 진행 중이면 먼저 완료해 주세요.", { confirmLocalDeletion = false }) {
        try { deleteLocalRecords(); confirmLocalDeletion = false; message = "기기 내 헬스 기록을 삭제했습니다." }
        catch (failure: Exception) { confirmLocalDeletion = false; message = failure.message }
    }
    TextButton({ action = "비밀번호 변경" }, enabled = !busy) { Text("비밀번호 변경") }
    TextButton({ action = "회원 탈퇴" }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("회원 탈퇴") }
    }
    action?.let { selected -> SocialSheet({ if (!busy) { action = null; current = ""; next = ""; repeat = "" } }) {
        Text(selected, style = MaterialTheme.typography.titleLarge)
        if (selected in listOf("회원 탈퇴", "비밀번호 변경")) AuthSecretField(current, { current = it }, "현재 비밀번호", !busy)
        if (selected == "비밀번호 변경") { AuthSecretField(next, { next = it }, "새 비밀번호", !busy); AuthSecretField(repeat, { repeat = it }, "새 비밀번호 확인", !busy) }
        Text(if (selected == "회원 탈퇴") "서버 계정을 탈퇴합니다. 이 작업은 되돌릴 수 없습니다." else "처리 후 로그인 화면으로 이동합니다.")
        message?.let { Text(it) }
        Button({
            if (selected == "비밀번호 변경" && (!validDemoPassword(next) || next != repeat || next.length > 64 || next.toByteArray().size > 72)) { message = "새 비밀번호 형식과 확인 값을 확인하세요." }
            else runAction({
                when (selected) {
                    "회원 탈퇴" -> { api.request("DELETE", "users/me", mapOf("password" to current)); api.clearSession() }
                    "비밀번호 변경" -> { api.request("PATCH", "users/me/password", mapOf("currentPassword" to current, "newPassword" to next)); api.clearSession() }
                    else -> {
                        // A stale local token must not trap the user on the signed-in screen.
                        runCatching { api.logout(selected == "전체 기기 로그아웃") }
                        api.clearSession()
                    }
                }
            }, { current = ""; next = ""; repeat = ""; finished() })
        }, enabled = !busy && (selected !in listOf("회원 탈퇴", "비밀번호 변경") || current.isNotBlank())) { Text(if (busy) "처리 중…" else "확인") }
    } }
}

@Composable internal fun ServerLogin(modifier: Modifier, api: FitMateApiClient, signedIn: () -> Unit, signup: () -> Unit) = WorkoutDesignTheme { Screen(modifier, background = MaterialTheme.colorScheme.background) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rememberAccount by remember { mutableStateOf(true) }
    var rememberedEmail by remember { mutableStateOf<String?>(null) }
    var loadingRemembered by remember { mutableStateOf(true) }
    var continuing by remember { mutableStateOf(false) }
    var errorFromRemembered by remember { mutableStateOf(false) }
    LaunchedEffect(api) {
        try { rememberedEmail = withContext(Dispatchers.IO) { api.rememberedEmail() } }
        catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error = failure.message ?: "기억한 계정을 불러오지 못했어요."
        } finally { loadingRemembered = false }
    }
    FitMateWordmark()
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        SocialScreenTitle("다시 만나서 반가워요", "계정으로 로그인하고 운동을 이어가세요.")
    }
    rememberedEmail?.let { savedEmail -> WorkoutCard {
        Text("기억한 계정", style = MaterialTheme.typography.titleMedium)
        Text(savedEmail, style = MaterialTheme.typography.bodyLarge)
        ErrorText(error.takeIf { errorFromRemembered })
        AuthAction(if (continuing) "접속 중…" else "이 계정으로 계속하기", enabled = !busy) {
            busy = true; continuing = true; error = null; errorFromRemembered = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.continueRememberedAccount() }
                    signedIn()
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    error = failure.message ?: "계정에 접속하지 못했어요. 다시 시도해 주세요."
                    rememberedEmail = withContext(Dispatchers.IO) { api.rememberedEmail() }
                    if (rememberedEmail == null) email = savedEmail
                } finally { busy = false; continuing = false }
            }
        }
        TextButton({
            busy = true; error = null; errorFromRemembered = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { api.clearSession() }
                    rememberedEmail = null
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    error = failure.message ?: "기억한 계정을 삭제하지 못했어요."
                } finally { busy = false }
            }
        }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("기억한 계정 삭제") }
        Text("삭제하면 이 기기의 간편 접속 정보만 지워집니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
    WorkoutCard {
    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("이메일") }, singleLine = true, enabled = !busy, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
    AuthSecretField(password, { password = it }, "비밀번호", !busy)
    AuthToggle("이 기기에 계정 기억", rememberAccount, { rememberAccount = it }, enabled = !busy,
        description = "최근 로그인한 계정 1개를 기억합니다. 로그아웃 후에도 비밀번호 없이 접속할 수 있어요.")
    ErrorText(error.takeIf { !errorFromRemembered || rememberedEmail == null })
    AuthAction(if (busy && !continuing) "로그인 중…" else "로그인", enabled = !busy && !loadingRemembered && validDemoEmail(email.trim()) && password.isNotEmpty()) {
        val submittedEmail = email.trim(); val submittedPassword = password
        busy = true; error = null; errorFromRemembered = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.authenticate("auth/login", mapOf("email" to submittedEmail, "password" to submittedPassword), rememberEmail = submittedEmail.takeIf { rememberAccount }) }
                password = ""; signedIn()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: "로그인에 실패했습니다."
            }
            finally { busy = false }
        }
    }
    }
    TextButton(signup, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("회원가입") }
} }

@Composable internal fun ServerSessionGate(api: FitMateApiClient, signup: @Composable (() -> Unit) -> Unit,
    launchReady: Boolean = true,
    content: @Composable (JsonObject) -> Unit) {
    var account by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var registration by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        loading = true; error = null
        try { account = withContext(Dispatchers.IO) { if (api.hasSession()) api.request("GET", "users/me").asJsonObject else null } }
        catch (failure: Exception) { error = failure.message ?: "계정을 불러오지 못했습니다." }
        finally { loading = false }
    }
    val sessionError = error
    when {
        loading || !launchReady -> LaunchScreen()
        account != null -> content(requireNotNull(account))
        sessionError != null -> WorkoutDesignTheme { Screen(Modifier.safeDrawingPadding(), background = MaterialTheme.colorScheme.background) {
            FitMateWordmark()
            StateCard("계정을 불러오지 못했어요", sessionError, androidx.compose.material.icons.Icons.Outlined.ErrorOutline) {
                AuthAction("다시 시도") { attempt++ }
                TextButton({ error = null; registration = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("다시 로그인") }
            }
        } }
        registration -> {
            androidx.activity.compose.BackHandler { registration = false }
            signup { registration = false; attempt++ }
        }
        else -> ServerLogin(Modifier.safeDrawingPadding(), api, { attempt++ }, { registration = true })
    }
}

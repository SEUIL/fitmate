package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun validDemoEmail(email: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)
internal fun validDemoPassword(value: String) = value.length >= 8 && value.any(Char::isLetter) && value.any(Char::isDigit)

@Composable internal fun SecretField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), singleLine = true)
}

/** UI-only verification. The displayed code is deliberately not a real credential. */
@Composable internal fun DemoEmailVerification(
    email: String,
    verified: Boolean,
    onVerified: (Boolean) -> Unit,
    checkAvailability: (suspend (String) -> Boolean)? = null
) {
    val scope = rememberCoroutineScope()
    var checked by remember(email) { mutableStateOf(false) }
    var sent by remember(email) { mutableStateOf(false) }
    var code by remember(email) { mutableStateOf("") }
    var expiresAt by remember(email) { mutableStateOf(0L) }
    var message by remember(email) { mutableStateOf<String?>(null) }
    LaunchedEffect(email) { onVerified(false) }
    Text("인증 UI 데모 · 이메일은 발송하지 않습니다. 테스트 코드: 123456", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({
            onVerified(false)
            if (!validDemoEmail(email)) {
                checked = false
                message = "이메일 형식을 확인하세요."
            } else if (checkAvailability == null) {
                checked = !email.equals("used@example.com", true)
                message = if (checked) "사용 가능한 이메일 예시입니다." else "이미 사용 중인 이메일 예시입니다."
            } else {
                scope.launch {
                    val available = runCatching { withContext(Dispatchers.IO) { checkAvailability(email) } }.getOrNull()
                    checked = available == true
                    message = when (available) {
                        true -> "사용 가능한 이메일입니다."
                        false -> "이미 사용 중인 이메일입니다."
                        null -> "이메일 중복 확인에 실패했습니다. 서버 연결을 확인하세요."
                    }
                }
            }
        }) { Text("중복 확인") }
        TextButton({ sent = true; expiresAt = System.currentTimeMillis() + 180_000; code = ""; onVerified(false); message = "발송 성공 예시 · 3분 이내 코드를 입력하세요." }, enabled = checked) { Text(if (sent) "코드 재발송" else "인증 코드 받기") }
    }
    if (sent && !verified) {
        OutlinedTextField(code, { code = it.take(6) }, Modifier.fillMaxWidth(), label = { Text("인증 코드") }, singleLine = true)
        TextButton({
            val ok = code == "123456" && System.currentTimeMillis() <= expiresAt
            onVerified(ok); message = if (ok) "인증 완료 (Mock)" else "코드가 다르거나 만료됐어요. 재발송 후 다시 시도하세요."
        }) { Text("인증 확인") }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable internal fun LoginFlow(modifier: Modifier, localEmail: String?, login: () -> Unit, signup: () -> Unit) = Screen(modifier) {
    var email by remember { mutableStateOf(localEmail.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("정상") }
    Text("fitmate", style = MaterialTheme.typography.headlineLarge)
    SectionTitle("다시 만나서 반가워요", "운동 기록을 이어가세요.")
    Text("로컬 UI 데모입니다. 실제 계정 인증은 하지 않으며 실제 비밀번호를 입력하지 마세요.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("이메일") }, singleLine = true)
    SecretField(password, { password = it }, "테스트 비밀번호")
    Text("테스트 입력: 영문·숫자 포함 8자 이상. 비밀번호는 보관하지 않습니다.", style = MaterialTheme.typography.bodySmall)
    PrimaryAction("로그인 (Mock)") {
        error = when {
            !validDemoEmail(email) || !validDemoPassword(password) -> "이메일과 테스트 비밀번호 형식을 확인하세요."
            localEmail == null -> "이 기기에 가입한 데모 프로필이 없어요. 먼저 회원가입을 진행하세요."
            !localEmail.equals(email.trim(), true) -> "이 기기에 저장된 데모 이메일과 일치하지 않아요."
            result != "정상" -> when (result) { "네트워크 오류" -> "연결 실패 예시입니다. 다시 시도하세요."; "정지" -> "이용이 제한된 계정 예시입니다."; else -> "이메일 또는 비밀번호가 일치하지 않는 예시입니다." }
            else -> null
        }
        if (error == null) { password = ""; login() }
    }
    ErrorText(error)
    TextButton({ reset = true }) { Text("비밀번호 재설정") }
    if (localEmail == null) TextButton(signup) { Text("회원가입") }
    DebugOnly {
        var tools by remember { mutableStateOf(false) }
        TextButton({ tools = !tools }) { Text("개발 및 테스트 도구") }
        if (tools) Choices(listOf("정상", "인증 오류", "정지", "네트워크 오류"), result) { result = it }
    }
    if (reset) SocialSheet({ reset = false }) { PasswordFlow(false, localEmail) }
}

@Composable internal fun PasswordFlow(change: Boolean, localEmail: String?) {
    var email by remember { mutableStateOf(localEmail.orEmpty()) }
    var verified by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Text(if (change) "비밀번호 변경" else "비밀번호 재설정", style = MaterialTheme.typography.titleLarge)
    Text("실제 비밀번호는 변경하지 않는 UI 데모입니다. 최종 정책은 API 계약에 따릅니다.")
    if (change) SecretField(current, { current = it }, "현재 테스트 비밀번호")
    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("이메일") })
    DemoEmailVerification(email, verified, onVerified = { verified = it })
    SecretField(next, { next = it }, "새 테스트 비밀번호")
    SecretField(repeat, { repeat = it }, "새 비밀번호 확인")
    PrimaryAction("변경 결과 확인 (Mock)") {
        result = when {
            !verified -> "이메일 인증을 완료해 주세요."
            change && !validDemoPassword(current) -> "현재 테스트 비밀번호 형식을 확인하세요."
            !validDemoPassword(next) -> "테스트 규칙: 영문·숫자 포함 8자 이상으로 입력하세요."
            next != repeat -> "새 비밀번호 확인이 일치하지 않아요."
            else -> { current = ""; next = ""; repeat = ""; "변경 성공 화면입니다. 서버 비밀번호는 변경되지 않았어요." }
        }
    }
    result?.let { Text(it) }
}

@Composable internal fun PolicyDetail(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text("검토용 안내 · 정식 약관/처리방침은 팀의 확정 문구로 교체해야 합니다.", color = MaterialTheme.colorScheme.error)
    when (title) {
        "이용약관" -> Text("운동 기록과 메이트 교류를 위한 서비스입니다. 타인의 권리를 침해하는 콘텐츠, 괴롭힘, 사칭은 제한 대상입니다. 운동 안내와 측정 정보는 의료 진단이 아닙니다. 서비스 제공자·시행일·제재/이의신청 절차는 정식 약관에서 확정합니다.")
        else -> Text("계정·프로필과 서버 루틴으로 진행한 운동 기록은 API 서버에 저장됩니다. 기기 전용 루틴·인바디 입력·리포트 목록은 현재 기기 내부에 보관됩니다. 건강정보와 위치정보는 별도 동의 대상으로 구분합니다. 이 화면은 개발 중 안내이며, 처리 주체·보유기간·수탁자·연락처를 포함한 정식 정책은 서비스 공개 전 확정이 필요합니다. 건강정보는 광고 타겟팅에 사용하지 않습니다.")
    }
}

@Composable internal fun AccountSettings(
    modifier: Modifier, email: String, health: Boolean, location: Boolean,
    setHealth: (Boolean) -> Unit, setLocation: (Boolean) -> Unit,
    logout: () -> Unit, withdraw: () -> Unit, deleteRecords: () -> Unit, external: () -> Unit
) = Screen(modifier) {
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    Text(email)
    TextButton({ sheet = "비밀번호 변경" }) { Text("비밀번호 변경") }
    TextButton({ sheet = "비밀번호 재설정" }) { Text("비밀번호 재설정") }
    TextButton({ sheet = "이용약관" }) { Text("이용약관 보기") }
    TextButton({ sheet = "개인정보 처리방침" }) { Text("개인정보 처리방침 보기") }
    ToggleLine("건강정보 처리 동의 (로컬)", health) { if (!it) confirm = "건강정보 동의 철회" else setHealth(true) }
    ToggleLine("위치정보 이용 동의 (로컬)", location) { if (!it) confirm = "위치정보 동의 철회" else setLocation(true) }
    Text("동의 철회와 기존 기록 삭제는 별개입니다. 위치 권한·Samsung Health 권한 해제는 기기 연결 관리에서 진행하세요.", style = MaterialTheme.typography.bodySmall)
    TextButton(external) { Text("Samsung Health 연결·권한 관리") }
    TextButton({ confirm = "로컬 운동 기록 삭제" }) { Text("내 기기의 헬스 기록·임시 운동 삭제") }
    TextButton({ notice = "서버에 저장된 외부 운동 기록 삭제 요청은 API 미연결로 전송되지 않았습니다." }) { Text("서버 외부 운동 기록 삭제 요청 (미연결)") }
    TextButton({ confirm = "회원 탈퇴" }) { Text("회원 탈퇴") }
    notice?.let { Text(it) }
    sheet?.let { value -> SocialSheet({ sheet = null }) {
        if (value.startsWith("비밀번호")) PasswordFlow(value == "비밀번호 변경", email) else PolicyDetail(value)
    } }
    confirm?.let { value -> ConfirmAction(value,
        when (value) {
            "회원 탈퇴" -> "로컬 데모 프로필·루틴·운동 기록과 앱 내 예시 데이터를 초기화합니다. 복구할 수 없습니다. 실제 서버 계정 탈퇴는 수행하지 않습니다."
            "로그아웃" -> "현재 운동을 일시정지하고 로그인 화면으로 이동합니다. 기기 내부 기록은 유지됩니다."
            "로컬 운동 기록 삭제" -> "이 기기에 보관한 헬스 기록과 진행 중 운동을 삭제합니다. 되돌릴 수 없습니다. 서버 기록은 삭제되지 않습니다."
            else -> "로컬 동의를 철회합니다. 기존 기록은 자동 삭제되지 않으며 서버에는 전달되지 않습니다."
        }, { confirm = null }) {
        when (value) {
            "회원 탈퇴" -> withdraw(); "로그아웃" -> logout(); "로컬 운동 기록 삭제" -> deleteRecords()
            "건강정보 동의 철회" -> setHealth(false); "위치정보 동의 철회" -> setLocation(false)
        }
        confirm = null
    } }
}

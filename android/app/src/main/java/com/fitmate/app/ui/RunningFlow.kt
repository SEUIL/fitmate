package com.fitmate.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fitmate.app.BuildConfig
import com.fitmate.app.running.RunTrackingService
import com.fitmate.app.running.RunTrackingStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.fitmate.app.data.remote.FitMateApiClient

internal data class RunLap(val number: Int, val meters: Int, val seconds: Int)
internal data class RunResult(val meters: Int, val seconds: Int, val laps: List<RunLap>, val goal: String, val target: Int, val rpe: Int, val condition: Int, val memo: String, val completedAt: Long? = null, val serverId: String? = null)
internal class RunningState {
    var phase by mutableStateOf("설정")
    var goal by mutableStateOf("거리")
    var targetDistance by mutableStateOf("5")
    var targetMinutes by mutableStateOf("30")
    var targetPace by mutableStateOf("420")
    var intervalMinutes by mutableStateOf("2")
    var autoPause by mutableStateOf(true)
    var recordRoute by mutableStateOf(false)
    var visibility by mutableStateOf("나만 보기")
    var samsung by mutableStateOf(false)
    var signal by mutableStateOf("정상")
    var demo by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var stationary by mutableStateOf(false)
    var seconds by mutableStateOf(0)
    var meters by mutableStateOf(0)
    var target by mutableStateOf(5000)
    var exitDialog by mutableStateOf(false)
    var laps by mutableStateOf(emptyList<RunLap>())
    var rpe by mutableStateOf("5")
    var condition by mutableStateOf("3")
    var memo by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var deviceTracking by mutableStateOf(false)
    var pendingUpload by mutableStateOf<String?>(null)
    var startedAt by mutableStateOf<Long?>(null)
    var completedAt by mutableStateOf<Long?>(null)
    val history = mutableStateListOf<RunResult>()

    fun tick(secondsToAdd: Int, metersToAdd: Int) {
        val beforeMeters = meters
        val beforeSeconds = seconds
        seconds += secondsToAdd
        meters += metersToAdd
        if (metersToAdd > 0) {
            for (km in (beforeMeters / 1000 + 1)..(meters / 1000)) {
                val crossingTime = beforeSeconds + ((km * 1000 - beforeMeters).toDouble() / metersToAdd * secondsToAdd).toInt()
                laps = laps + RunLap(km, 1000, crossingTime - laps.sumOf { it.seconds })
            }
        }
    }
    fun updateFromDeviceTracking(trackedSeconds: Int, trackedMeters: Int) {
        val beforeMeters = meters
        val beforeSeconds = seconds
        seconds = trackedSeconds
        meters = trackedMeters
        val metersAdded = trackedMeters - beforeMeters
        val secondsAdded = (trackedSeconds - beforeSeconds).coerceAtLeast(0)
        if (metersAdded > 0) {
            for (km in (beforeMeters / 1000 + 1)..(trackedMeters / 1000)) {
                val crossingTime = beforeSeconds + ((km * 1000 - beforeMeters).toDouble() / metersAdded * secondsAdded).toInt()
                laps = laps + RunLap(km, 1000, crossingTime - laps.sumOf { it.seconds })
            }
        }
    }
    fun reset() {
        phase = "설정"; paused = false; stationary = false; seconds = 0; meters = 0; laps = emptyList()
        error = null; memo = ""; rpe = "5"; condition = "3"; exitDialog = false; deviceTracking = false; pendingUpload = null; startedAt = null; completedAt = null
    }
}

internal fun runPace(seconds: Int, meters: Int): String {
    if (meters <= 0 || seconds <= 0) return "—"
    val pace = (seconds * 1000.0 / meters).toInt()
    return "%d:%02d /km".format(pace / 60, pace % 60)
}

@Composable internal fun RunningFlow(
    modifier: Modifier,
    state: RunningState,
    locationConsent: Boolean,
    setLocationConsent: (Boolean) -> Unit,
    api: FitMateApiClient,
    home: (Boolean) -> Unit
) = with(state) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var debugExpanded by remember { mutableStateOf(false) }
    val tracking by RunTrackingStore.updates.collectAsState()
    var consentDialog by remember { mutableStateOf(false) }
    fun startDeviceTracking() {
        if (startedAt == null) startedAt = System.currentTimeMillis()
        deviceTracking = true; paused = false; stationary = false; signal = "위치 확인 중"; phase = "진행"
        RunTrackingService.start(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startDeviceTracking()
        else error = "정확한 거리와 페이스 기록을 위해 위치 권한을 허용해 주세요."
    }
    LaunchedEffect(tracking) {
        if (tracking.active) {
            deviceTracking = true; phase = "진행"; paused = tracking.paused
            updateFromDeviceTracking(tracking.elapsedSeconds, tracking.meters)
            signal = tracking.message ?: "정상"; stationary = tracking.currentSpeedMps?.let { it < 0.5f } ?: false
        } else if (deviceTracking && tracking.message != null) error = tracking.message
    }
    BackHandler(phase != "설정") { if (!saving) exitDialog = true }
    val autoPaused = autoPause && stationary && !deviceTracking
    LaunchedEffect(phase, paused, autoPaused, demo, signal, deviceTracking) {
        while (phase == "진행" && !paused && !autoPaused && !deviceTracking) {
            delay(1000)
            tick(1, if (demo && signal == "정상") 3 else 0)
        }
    }
    if (exitDialog) ConfirmAction("러닝을 나갈까요?", "저장하지 않은 이번 기록은 사라집니다.", { exitDialog = false }) { RunTrackingService.stop(context); reset(); home(false) }
    if (consentDialog) ConfirmAction(
        "위치정보 이용 동의",
        "러닝 중 거리·페이스·랩을 계산하기 위해 위치정보를 사용합니다. 지도 경로나 좌표는 저장하지 않습니다. 운동을 종료하면 GPS 기록도 중지됩니다.",
        { consentDialog = false }
    ) { consentDialog = false; setLocationConsent(true); permissionLauncher.launch(buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()) }
    Column(modifier.fillMaxSize()) {
    Screen(Modifier.weight(1f)) {
        WorkoutHeading(if (phase == "설정") "오늘의 목표" else "러닝 $phase",
            when { phase == "리포트" -> "저장 전 · 오늘의 기록을 확인하세요."; deviceTracking -> "실제 GPS 기록 중 · 알림에서 기록 상태를 확인할 수 있어요."; demo -> "예시 GPS 데이터"; else -> "이번 러닝의 목표를 선택하세요." })
        when (phase) {
            "설정" -> {
                WorkoutChoices(listOf("자유", "거리", "시간", "페이스", "인터벌"), goal) { goal = it }
                WorkoutCard {
                    Text("운동 목표 · $goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (goal == "자유") Text("정해진 목표 없이 내 페이스에 맞춰 달려요.")
                    if (goal in listOf("거리", "페이스")) OutlinedTextField(targetDistance, { targetDistance = it }, label = { Text("목표 거리 (km)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), supportingText = { Text("0.1~100 km") })
                    if (goal in listOf("시간", "인터벌")) {
                        Text("목표 시간")
                        WorkoutChoices(listOf("10분", "20분", "30분", "60분"), "${targetMinutes}분", columns = 2) { targetMinutes = it.removeSuffix("분") }
                    }
                    if (goal == "페이스") {
                        Text("목표 페이스")
                        WorkoutChoices(listOf("5:00/km", "6:00/km", "7:00/km", "8:00/km"), "${targetPace.toInt() / 60}:00/km", columns = 2) { targetPace = (it.substringBefore(":").toInt() * 60).toString() }
                    }
                    if (goal == "인터벌") {
                        Text("달리기 / 걷기 동일 구간 (분)")
                        WorkoutChoices(listOf("1분", "2분", "3분", "5분"), "${intervalMinutes}분", columns = 2) { intervalMinutes = it.removeSuffix("분") }
                    }
                }
                WorkoutCard {
                    Text("기록 설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ToggleLine("자동 일시정지", autoPause) { autoPause = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleLine("경로 기록", recordRoute) { recordRoute = it }
                    Text("거리·시간·랩만 기록하며 지도 경로와 GPS 좌표는 저장하지 않아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (recordRoute) WorkoutChoices(listOf("나만 보기", "요약만 공유"), visibility, columns = 2) { visibility = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleLine("Samsung Health 사용", samsung) { samsung = it }
                    Text("실제 GPS는 러닝 시작 후 위치 권한을 허용하면 사용합니다. Samsung Health 수신 값은 연결 관리에서 별도로 확인하세요.", style = MaterialTheme.typography.bodySmall)
                }
                Text("시작 시 위치정보 이용 동의와 위치 권한을 확인해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (BuildConfig.DEBUG) WorkoutCard {
                    TextButton({ debugExpanded = !debugExpanded }) { Text(if (debugExpanded) "개발 및 테스트 도구 접기" else "개발 및 테스트 도구") }
                    if (debugExpanded) {
                        ToggleLine("테스트용 예시 GPS 사용", demo) { demo = it }
                        Text("Release 앱에는 표시되지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ErrorText(error)
                WorkoutAction("러닝 시작") {
                    val distance = targetDistance.toDoubleOrNull()
                    if (goal in listOf("거리", "페이스") && (distance == null || !distance.isFinite() || distance !in 0.1..100.0)) error = "목표 거리는 0.1~100 km로 입력하세요."
                    else {
                        target = if (goal in listOf("거리", "페이스")) (distance!! * 1000).toInt() else targetMinutes.toInt() * 60
                        error = null; paused = false
                        if (demo) { startedAt = System.currentTimeMillis(); deviceTracking = false; phase = "진행" }
                        else if (locationConsent) permissionLauncher.launch(buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray())
                        else consentDialog = true
                    }
                }
                if (history.isNotEmpty()) SoftCard { Text("저장한 러닝"); history.forEach { Text("${"%.2f".format(it.meters / 1000.0)} km · ${it.seconds / 60}분 · ${runPace(it.seconds, it.meters)}") } }
            }
            "진행" -> {
                Text(if (paused) "일시정지" else if (autoPaused) "정지 감지 · 자동 일시정지" else "달리는 중", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("운동 시간", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60), style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("이동 거리", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.2f".format(meters / 1000.0)} km", style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                }
                WorkoutCard {
                    WorkoutMetrics("현재 페이스" to if ((demo || deviceTracking) && signal == "정상" && !paused && !autoPaused) runPace(60, (tracking.currentSpeedMps?.times(60f) ?: 0f).toInt()).takeIf { deviceTracking } ?: "5:33 /km" else "—",
                        "평균 페이스" to runPace(seconds, meters))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("속도 ${tracking.currentSpeedMps?.let { "%.1f".format(it * 3.6f) } ?: if (demo && signal == "정상" && !paused && !autoPaused) "10.8" else "—"} km/h")
                }
                if (goal != "자유") WorkoutCard {
                        Text("$goal 목표", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (goal in listOf("거리", "페이스")) "${"%.2f".format(target / 1000.0)} km" else "${target / 60}분")
                        val completed = if (goal in listOf("거리", "페이스")) meters else seconds
                        val displayedProgress = animatedProgress(completed.toFloat() / target.coerceAtLeast(1))
                        LinearProgressIndicator(progress = { displayedProgress }, modifier = Modifier.fillMaxWidth())
                        Text("목표 진행 ${(completed * 100L / target.coerceAtLeast(1)).coerceAtMost(100)}%")
                    if (goal == "인터벌") Text("현재 구간 · ${if (seconds / (intervalMinutes.toInt() * 60) % 2 == 0) "달리기" else "걷기"}")
                }
                WorkoutCard {
                    Text("GPS · $signal", fontWeight = FontWeight.SemiBold)
                    if (signal != "정상") Text("GPS ${signal} · 시간 기록은 유지됩니다.", color = MaterialTheme.colorScheme.error)
                    if (deviceTracking) Text(tracking.accuracyMeters?.let { "GPS 정확도 약 ${it.toInt()}m" } ?: "GPS 위치를 찾는 중")
                    if (samsung) Text("심박수 — · Samsung Health 수신 대기")
                }
                WorkoutCard {
                    Text("1 km 자동 랩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (laps.isEmpty()) Text("1 km를 달리면 첫 랩이 기록됩니다.")
                    laps.forEach { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Text("${it.number}랩 · 1.00 km · ${runPace(it.seconds, it.meters)}") }
                }
                if (demo && BuildConfig.DEBUG) WorkoutCard {
                    Text("테스트 도구 · 실제 GPS가 아닙니다")
                    Choices(listOf("정상", "끊김", "권한 없음"), signal) { signal = it }
                    ToggleLine("정지 감지", stationary) { stationary = it }
                    OutlinedButton({ if (!paused && !autoPaused) tick(360, if (signal == "정상") 1000 else 0) }, enabled = !paused && !autoPaused) { Text("6분 진행 (+1 km, 정상 GPS)") }
                }
                OutlinedButton({ exitDialog = true }) { Text("기록 취소") }
            }
            else -> {
                WorkoutCard {
                    val achieved = when (goal) {
                        "거리" -> meters >= target
                        "페이스" -> meters >= target && meters > 0 && seconds * 1000.0 / meters <= targetPace.toInt()
                        "시간", "인터벌" -> seconds >= target
                        else -> null
                    }
                    Text(when (achieved) { true -> "목표 달성!"; false -> "목표 미달성 · 오늘의 기록을 남겨보세요."; null -> "자유 달리기를 마쳤어요." }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    WorkoutMetrics("거리" to "${"%.2f".format(meters / 1000.0)} km", "운동 시간" to "${seconds / 60}분 ${seconds % 60}초")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WorkoutMetrics("평균 페이스" to runPace(seconds, meters), "최고 랩 페이스" to (laps.minByOrNull { it.seconds }?.let { runPace(it.seconds, it.meters) } ?: "—"))
                    Text("칼로리·심박수·케이던스·고도: 수신 데이터 없음")
                    if (recordRoute) Text("경로: 수신 데이터 없음 · 공개 범위 $visibility")
                    if (meters == 0) Text("거리 기록이 없어요. 시간과 메모만 저장할 수 있습니다.")
                }
                WorkoutCard {
                    Text("구간별 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    laps.forEach { Text("${it.number}랩 · 1 km · ${runPace(it.seconds, it.meters)}") }
                    val partial = meters - laps.sumOf { it.meters }
                    if (partial > 0) Text("마지막 구간 · ${partial}m · ${runPace(seconds - laps.sumOf { it.seconds }, partial)}")
                    if (meters == 0) Text("기록된 랩이 없습니다.")
                    val previous = history.firstOrNull { it.meters == meters && meters > 0 }
                    Text(previous?.let { "이전 동일 거리 대비 ${seconds - it.seconds}초" } ?: "동일 거리의 이전 기록이 없습니다.")
                    val best = history.filter { it.meters == meters && meters > 0 }.minOfOrNull { it.seconds }
                    if (best != null && seconds < best) Text("동일 거리 개인 최고 기록!")
                }
                if (pendingUpload == null) WorkoutCard {
                    Text("오늘의 컨디션", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("RPE (1~10)"); WorkoutChoices((1..10).map(Int::toString), rpe, columns = 5) { rpe = it }
                    Text("컨디션 (1 나쁨 ~ 5 좋음)"); WorkoutChoices((1..5).map(Int::toString), condition, columns = 5) { condition = it }
                    OutlinedTextField(memo, { memo = it.take(500) }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth())
                    Text("가볍게 걷고 수분을 보충하며 마무리하세요.")
                }
                if (pendingUpload != null) Text("저장 요청한 기록을 확인 중입니다. 다시 시도해도 같은 기록으로 저장됩니다.", style = MaterialTheme.typography.bodySmall)
                ErrorText(error)
                WorkoutAction(if (saving) "서버에 저장 중…" else "기록 저장하고 홈으로", enabled = !saving) {
                    if (!saving) {
                        saving = true; error = null
                        scope.launch {
                            try {
                                val finishedAt = completedAt ?: System.currentTimeMillis().also { completedAt = it }
                                val body = pendingUpload?.let { com.google.gson.JsonParser.parseString(it).asJsonObject }
                                    ?: com.google.gson.Gson().toJsonTree(runningPayload(state, finishedAt) + ("clientRecordId" to java.util.UUID.randomUUID().toString())).asJsonObject.also { pendingUpload = it.toString() }
                                val saved = withContext(Dispatchers.IO) { ServerRecordApi(api).saveRun(body) }
                                history.add(0, RunResult(saved.recordInt("distanceMeters") ?: 0, saved.recordInt("activeDurationSeconds") ?: 0,
                                    saved.rows("laps").map { RunLap(requireNotNull(it.recordInt("lapNo")), requireNotNull(it.recordInt("distanceMeters")), requireNotNull(it.recordInt("durationSeconds"))) },
                                    goal, target, saved.recordInt("rpe") ?: 5, saved.recordInt("conditionScore") ?: 3, saved.text("memo"), saved.recordTime("completedAt"), saved.text("id")))
                                reset(); home(true)
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                error = "${failure.message ?: "저장하지 못했어요."} 입력한 기록은 유지됩니다."
                            } finally { saving = false }
                        }
                    }
                }
            }
        }
    }
    if (phase == "진행") Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                WorkoutAction(if (paused) "재개" else "일시정지") {
                    if (deviceTracking) { if (paused) RunTrackingService.resume(context) else RunTrackingService.pause(context) } else paused = !paused
                }
            }
            Box(Modifier.weight(1f)) {
                WorkoutSecondaryAction("종료") { RunTrackingService.stop(context); deviceTracking = false; completedAt = System.currentTimeMillis(); phase = "리포트" }
            }
        }
    }
    }
}

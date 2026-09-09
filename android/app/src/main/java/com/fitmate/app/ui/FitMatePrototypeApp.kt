package com.fitmate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.MatePost
import com.fitmate.app.data.model.MateDetails
import com.fitmate.app.data.model.RecruitmentDraft
import com.fitmate.app.data.model.CommunityPost
import com.fitmate.app.data.model.DirectMessage
import com.fitmate.app.data.model.DmConversation
import com.fitmate.app.data.model.InbodyRecord
import com.fitmate.app.data.model.Sport
import com.fitmate.app.data.model.UserProfile
import com.fitmate.app.data.model.WorkoutSet
import com.fitmate.app.data.model.WorkoutSummary
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.ApiExercise
import com.fitmate.app.data.remote.ServerMateApi
import com.fitmate.app.data.remote.ServerInbodyApi
import com.fitmate.app.data.remote.ServerSocialApi
import com.fitmate.app.data.repository.MockFitMateRepository
import com.fitmate.app.running.RunTrackingService
import com.fitmate.app.running.RunTrackingStore
import com.fitmate.app.samsunghealth.SamsungHealthStatus
import com.fitmate.app.samsunghealth.SamsungHealthUiState
import com.fitmate.app.samsunghealth.SamsungWorkoutMetrics
import com.fitmate.app.ui.theme.FitMateColors
import com.fitmate.app.ui.theme.FitMateShapes
import com.fitmate.app.ui.theme.FitMateSpacing
import com.fitmate.app.ui.theme.FitmateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PrototypeRoute(val title: String) {
    PERSON("프로필"),
    LOGIN("로그인"), SETTINGS("설정"), APPEARANCE("화면 모드"), FOLLOW_REQUESTS("팔로우 요청"), ACCOUNT("계정·개인정보"), NOTIFICATIONS("알림"), REPORTS("신고 내역"), HISTORY("운동 기록"),
    ONBOARDING("운동 프로필"), HOME("FitMate"), SOLO("운동 선택"), HEALTH_ROUTINE("헬스 루틴 선택"), WORKOUT_PREP("운동 준비"), RUNNING("러닝"),
    MATE_LIST("같이 운동하기"), MATE_DETAIL("메이트 게시물"), MATE_WRITE("모집 게시물 작성"),
    WORKOUT("운동 진행"), REPORT("운동 리포트"), INBODY("인바디 등록"), COMMUNITY("커뮤니티"),
    DM("DM"), DM_DETAIL("대화"), PROFILE("프로필 · 설정"), SAFETY("안전 · 신고"), ADMIN("관리자"), ADS("제휴 광고")
}

private data class WorkoutRoutine(
    val name: String,
    val description: String,
    val firstExerciseName: String,
    val exercises: List<String> = listOf(firstExerciseName).filter { it.isNotBlank() }
)

private val MockHealthRoutines = listOf(
    WorkoutRoutine("초급 전신", "전신을 고르게 사용하는 기본 6종 루틴", "랫풀다운"),
    WorkoutRoutine("등 · 이두 집중", "등과 이두를 중심으로 진행하는 저장 루틴", "시티드 로우"),
    WorkoutRoutine("직접 구성", "운동 진행 화면에서 원하는 운동을 추가해 구성", "")
)

private val Mint: Color @Composable get() = FitMateColors.Primary
private val MintSoft: Color @Composable get() = FitMateColors.PrimaryContainer
private val Lavender: Color @Composable get() = FitMateColors.Background
private val Ink = FitMateColors.DarkSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitMatePrototypeApp(
    onOpenSamsungTest: () -> Unit,
    samsungHealthState: SamsungHealthUiState = SamsungHealthUiState(),
    onRefreshSamsungHealth: () -> Unit = {}
) {
    var generation by remember { mutableStateOf(0) }
    key(generation) {
        val api = rememberFitMateApiClient()
        ServerSessionGate(api, signup = { done -> OnboardingScreen(Modifier.safeDrawingPadding(), api) { done() } }) { account ->
            key(account.text("id")) {
                FitMateAppContent(onOpenSamsungTest, samsungHealthState, onRefreshSamsungHealth, api, account) { generation++ }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FitMateAppContent(
    onOpenSamsungTest: () -> Unit,
    samsungHealthState: SamsungHealthUiState,
    onRefreshSamsungHealth: () -> Unit,
    apiClient: FitMateApiClient,
    serverAccount: com.google.gson.JsonObject,
    resetApp: () -> Unit
) {
    val context = LocalContext.current
    val localStore = remember { FrontendLocalStore(context, serverAccount.text("id")) }
    val initial = remember { localStore.load() }
    if (localStore.readFailed) {
        AlertDialog(onDismissRequest = {}, title = { Text("로컬 기록을 읽지 못했어요") },
            text = { Text("저장된 파일은 덮어쓰지 않았습니다. 앱을 종료하고 개발자에게 확인하거나, 복구할 수 없는 로컬 데모 기록을 초기화한 뒤 다시 시작할 수 있습니다.") },
            confirmButton = { TextButton({ if (localStore.clearAndClose()) resetApp() }) { Text("로컬 데모 기록 초기화") } })
        return
    }
    var signedIn by remember { mutableStateOf(true) }
    var registered by remember { mutableStateOf(true) }
    var healthConsent by remember { mutableStateOf(initial.healthConsent) }
    var locationConsent by remember { mutableStateOf(initial.locationConsent) }
    var savedRoutines by remember { mutableStateOf(initial.routines) }
    var storageError by remember { mutableStateOf(localStore.readFailed) }
    val exerciseCatalog = rememberExerciseCatalog(apiClient)
    val serverMate = remember(apiClient) { ServerMateApi(apiClient) }
    val serverInbody = remember(apiClient) { ServerInbodyApi(apiClient) }
    val serverSocial = remember(apiClient) { ServerSocialApi(apiClient) }
    val repository = remember { MockFitMateRepository }
    val screenStates = rememberSaveableStateHolder()
    var route by remember { mutableStateOf(if (signedIn) PrototypeRoute.HOME else PrototypeRoute.LOGIN) }
    var profile by remember { mutableStateOf(serverAccount.toUserProfile()) }
    var profileVersion by remember { mutableStateOf(serverAccount.getAsJsonObject("profile").get("version").asLong) }
    var matePosts by remember { mutableStateOf(emptyList<MatePost>()) }
    val mateServerState = remember { MateServerState() }
    var selectedMatePost by remember { mutableStateOf(matePosts.firstOrNull() ?: repository.matePosts().first()) }
    var latestWorkoutSummary by remember { mutableStateOf(WorkoutSummary(0, emptyList(), false)) }
    var latestServerSessionId by remember { mutableStateOf<String?>(null) }
    var workoutHistory by remember { mutableStateOf(initial.history) }
    var selectedWorkoutRoutine by remember { mutableStateOf(MockHealthRoutines.first()) }
    var selectedServerRoutine by remember { mutableStateOf<com.google.gson.JsonObject?>(null) }
    var localRoutineMode by remember { mutableStateOf(false) }
    var workoutSession by remember { mutableStateOf(initial.workout?.restore()) }
    val integrationScope = rememberCoroutineScope()
    var startingWorkout by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    val serverWorkout = remember(workoutSession) { workoutSession?.let { ServerWorkout(apiClient, it) } }
    LaunchedEffect(workoutSession) {
        val current = workoutSession ?: return@LaunchedEffect
        if (current.serverState != null) snapshotFlow { WorkoutUpload(current.recordedSets.toList(), current.isTimerRunning) }.conflate().collect { upload ->
            try { withContext(Dispatchers.IO) { serverWorkout?.synchronize(upload) }; current.syncError = null }
            catch (failure: Exception) { if (failure is kotlinx.coroutines.CancellationException) throw failure; current.syncError = failure.message }
        }
    }
    var workoutExit by remember { mutableStateOf(false) }
    var cancelWorkout by remember { mutableStateOf(false) }
    var cancellingWorkout by remember { mutableStateOf(false) }
    var cancelError by remember { mutableStateOf<String?>(null) }
    var inbodyOrigin by remember { mutableStateOf(PrototypeRoute.HOME) }
    val runningState = remember { initial.running?.restore() ?: RunningState() }
    val inbodyState = remember { InbodyState() }
    val mateState = remember { MateFeatureState().also { initial.mates?.restore(it) } }
    val socialState = remember { SocialState(repository.communityPosts(), repository.dmConversations()).also { it.accounts.photo = initial.photo; initial.social?.restore(it) } }
    var currentChat by remember { mutableStateOf<ChatRoom?>(null) }
    var personOrigin by remember { mutableStateOf(PrototypeRoute.COMMUNITY) }
    var profileEditRequest by remember { mutableStateOf(0) }
    LaunchedEffect(socialState.accounts.opened) {
        if (socialState.accounts.opened != null && route != PrototypeRoute.PERSON) {
            personOrigin = route; route = PrototypeRoute.PERSON
        }
    }
    LaunchedEffect(exerciseCatalog) { exerciseCatalog.refresh() }
    LaunchedEffect(signedIn, apiClient) {
        if (!signedIn) return@LaunchedEffect
        try { matePosts = withContext(Dispatchers.IO) { serverMate.posts() } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { startError = "메이트 목록을 불러오지 못했습니다. 같이 운동하기에서 다시 확인해 주세요." }
    }
    val currentSnapshot by rememberUpdatedState {
        FrontendSnapshot(profile = if (registered) profile else null, signedIn = signedIn,
            workout = workoutSession?.snapshot(), history = workoutHistory, routines = savedRoutines,
            photo = socialState.accounts.photo, healthConsent = healthConsent, locationConsent = locationConsent, running = runningState.snapshot(),
            social = socialState.snapshot(), body = if (inbodyState.loaded) inbodyState.records.map { it.snapshot() } else initial.body, mates = mateState.snapshot(matePosts))
    }
    LaunchedEffect(localStore) {
        snapshotFlow { currentSnapshot() }.conflate().collect { snapshot ->
            val revision = localStore.revision()
            storageError = !withContext(Dispatchers.IO) { localStore.save(snapshot, revision) }
            delay(1_000)
        }
    }
    DisposableEffect(context) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                workoutSession?.pause()
                if (!RunTrackingStore.updates.value.active) runningState.paused = true
                storageError = !localStore.save(currentSnapshot())
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    if (!signedIn) {
        BackHandler(route == PrototypeRoute.ONBOARDING) { route = PrototypeRoute.LOGIN }
        if (route == PrototypeRoute.ONBOARDING) OnboardingScreen(Modifier.safeDrawingPadding(), apiClient, onHealthConsent = { healthConsent = it }) {
            profile = it; registered = true; signedIn = true; route = PrototypeRoute.HOME
        } else LoginFlow(Modifier.safeDrawingPadding(), if (registered) profile.email else null,
            login = { signedIn = true; route = PrototypeRoute.HOME }, signup = { route = PrototypeRoute.ONBOARDING })
        return
    }
    CommunityAccountSheets(socialState, { room -> currentChat = room; route = PrototypeRoute.DM_DETAIL }, serverSocial, serverAccount.text("id"))
    SocialDelivery(socialState)
    if (cancelWorkout) AlertDialog(
        onDismissRequest = { if (!cancellingWorkout) cancelWorkout = false },
        title = { Text("이번 운동을 취소할까요?") },
        text = { Text(cancelError ?: "취소한 운동은 완료 기록과 리포트에 포함되지 않습니다.") },
        confirmButton = { TextButton({
            cancellingWorkout = true; cancelError = null
            integrationScope.launch {
                try {
                    if (workoutSession?.serverState != null) withContext(Dispatchers.IO) { serverWorkout?.cancel() }
                    workoutSession = null; mateState.activeWorkoutId = null; cancelWorkout = false; route = PrototypeRoute.HOME
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    cancelError = failure.message
                } finally { cancellingWorkout = false }
            }
        }, enabled = !cancellingWorkout) { Text(if (cancellingWorkout) "취소 중…" else "운동 취소") } },
        dismissButton = { TextButton({ cancelWorkout = false }, enabled = !cancellingWorkout) { Text("계속 운동") } }
    )
    if (workoutExit) AlertDialog(
        onDismissRequest = { workoutExit = false },
        title = { Text("운동을 잠시 멈출까요?") },
        text = { Text("세트와 타이머를 기기에 보관하고 홈으로 이동합니다. 재실행 시 일시정지 상태로 이어갈 수 있어요.") },
        confirmButton = { TextButton({ workoutSession?.pause(); workoutExit = false; route = PrototypeRoute.HOME }) { Text("잠시 나가기") } },
        dismissButton = { TextButton({ workoutExit = false }) { Text("계속 운동") } }
    )
    BackHandler(route != PrototypeRoute.HOME && route != PrototypeRoute.ONBOARDING) {
        if (route == PrototypeRoute.PERSON) { socialState.accounts.opened = null; route = personOrigin; return@BackHandler }
        if (route == PrototypeRoute.WORKOUT) { workoutExit = true; return@BackHandler }
        route = when (route) {
            PrototypeRoute.INBODY -> inbodyOrigin
            PrototypeRoute.DM_DETAIL -> if (socialState.accounts.opened != null) PrototypeRoute.PERSON else PrototypeRoute.DM
            PrototypeRoute.MATE_LIST, PrototypeRoute.MATE_DETAIL, PrototypeRoute.MATE_WRITE -> if (mateServerState.back()) PrototypeRoute.MATE_LIST else PrototypeRoute.HOME
            PrototypeRoute.HEALTH_ROUTINE, PrototypeRoute.RUNNING -> PrototypeRoute.SOLO
            PrototypeRoute.WORKOUT_PREP -> PrototypeRoute.HEALTH_ROUTINE
            PrototypeRoute.SETTINGS, PrototypeRoute.APPEARANCE, PrototypeRoute.FOLLOW_REQUESTS, PrototypeRoute.ACCOUNT, PrototypeRoute.HISTORY, PrototypeRoute.REPORTS -> PrototypeRoute.PROFILE
            else -> PrototypeRoute.HOME
        }
    }
    Scaffold(
        topBar = {
            if (route !in setOf(PrototypeRoute.HOME, PrototypeRoute.ONBOARDING, PrototypeRoute.COMMUNITY, PrototypeRoute.PROFILE, PrototypeRoute.PERSON, PrototypeRoute.MATE_LIST, PrototypeRoute.DM)) {
                TopAppBar(
                    title = { Text(if (route == PrototypeRoute.INBODY && inbodyState.phase in setOf("상세", "기록 상세")) "신체 구성 상세" else if (route == PrototypeRoute.DM_DETAIL) currentChat?.name.orEmpty() else if (route == PrototypeRoute.PERSON) socialState.accounts.opened.orEmpty() else route.title) },
                    navigationIcon = {
                        Text("‹", modifier = Modifier.padding(horizontal = 20.dp).clickable {
                            if (route == PrototypeRoute.PERSON) { socialState.accounts.opened = null; route = personOrigin; return@clickable }
                            if (route == PrototypeRoute.WORKOUT) { workoutExit = true; return@clickable }
                            if (route == PrototypeRoute.RUNNING && runningState.phase != "설정") {
                                runningState.exitDialog = true
                                return@clickable
                            }
                            if (route == PrototypeRoute.INBODY && inbodyState.phase != "목록") {
                                if (!inbodyState.busy) inbodyState.phase = if (inbodyState.phase == "상세") "검수" else "목록"
                                return@clickable
                            }
                            route = when (route) {
                                PrototypeRoute.INBODY -> inbodyOrigin
                                PrototypeRoute.DM_DETAIL -> if (socialState.accounts.opened != null) PrototypeRoute.PERSON else PrototypeRoute.DM
                                PrototypeRoute.MATE_LIST, PrototypeRoute.MATE_DETAIL, PrototypeRoute.MATE_WRITE -> if (mateServerState.back()) PrototypeRoute.MATE_LIST else PrototypeRoute.HOME
                                PrototypeRoute.HEALTH_ROUTINE -> PrototypeRoute.SOLO
                                PrototypeRoute.WORKOUT_PREP -> PrototypeRoute.HEALTH_ROUTINE
                                PrototypeRoute.WORKOUT -> PrototypeRoute.WORKOUT_PREP
                                PrototypeRoute.RUNNING -> PrototypeRoute.SOLO
                                PrototypeRoute.SETTINGS, PrototypeRoute.APPEARANCE, PrototypeRoute.FOLLOW_REQUESTS, PrototypeRoute.ACCOUNT, PrototypeRoute.HISTORY, PrototypeRoute.REPORTS -> PrototypeRoute.PROFILE
                                else -> PrototypeRoute.HOME
                            }
                        }, style = MaterialTheme.typography.headlineMedium)
                    }
                )
            }
        },
        bottomBar = {
            if (route in setOf(PrototypeRoute.HOME, PrototypeRoute.MATE_LIST, PrototypeRoute.COMMUNITY, PrototypeRoute.DM, PrototypeRoute.PROFILE) && (route != PrototypeRoute.MATE_LIST || mateServerState.page == "목록")) {
                BottomNavigation(route) { route = it }
            }
        }
    ) { padding ->
        val rootTab = route in setOf(PrototypeRoute.HOME, PrototypeRoute.MATE_LIST, PrototypeRoute.COMMUNITY, PrototypeRoute.DM, PrototypeRoute.PROFILE)
        val depth = when (route) {
            PrototypeRoute.HOME, PrototypeRoute.MATE_LIST, PrototypeRoute.COMMUNITY, PrototypeRoute.DM, PrototypeRoute.PROFILE -> 0
            PrototypeRoute.SOLO, PrototypeRoute.PERSON, PrototypeRoute.MATE_DETAIL, PrototypeRoute.ACCOUNT, PrototypeRoute.INBODY -> 1
            PrototypeRoute.HEALTH_ROUTINE, PrototypeRoute.RUNNING, PrototypeRoute.DM_DETAIL -> 2
            PrototypeRoute.WORKOUT_PREP -> 3
            PrototypeRoute.WORKOUT, PrototypeRoute.REPORT -> 4
            else -> 1
        }
        RouteEntrance(route.name, rootTab, depth) {
        screenStates.SaveableStateProvider(route.name) {
        when (route) {
            PrototypeRoute.PERSON -> MemberProfileScreen(Modifier.padding(padding), socialState, back = { socialState.accounts.opened = null; route = personOrigin }, dm = { room -> currentChat = room; route = PrototypeRoute.DM_DETAIL }, server = serverSocial)
            PrototypeRoute.LOGIN -> Unit
            PrototypeRoute.SETTINGS -> SettingsScreen(
                Modifier.padding(padding), profile,
                onProfileEdit = { profileEditRequest++; route = PrototypeRoute.PROFILE },
                onSamsungHealth = onOpenSamsungTest,
                onAccount = { route = PrototypeRoute.ACCOUNT },
                onAppearance = { route = PrototypeRoute.APPEARANCE },
                onNotifications = { route = PrototypeRoute.NOTIFICATIONS },
                onFollowRequests = { route = PrototypeRoute.FOLLOW_REQUESTS },
                onReports = { route = PrototypeRoute.REPORTS },
                onOpenAdmin = { route = PrototypeRoute.ADMIN },
            )
            PrototypeRoute.APPEARANCE -> Screen(Modifier.padding(padding)) {
                SectionTitle("화면 모드", "앱 전체에 적용할 테마를 선택하세요.")
                com.fitmate.app.ui.theme.AppearanceSettings()
            }
            PrototypeRoute.FOLLOW_REQUESTS -> FollowRequestsScreen(Modifier.padding(padding), socialState, serverSocial)
            PrototypeRoute.ACCOUNT -> ServerAccountSettings(Modifier.padding(padding), apiClient,
                finished = { workoutSession?.pause(); RunTrackingService.stop(context); resetApp() }, external = onOpenSamsungTest,
                health = healthConsent, setHealth = { healthConsent = it }, location = locationConsent, setLocation = { locationConsent = it },
                deleteLocalRecords = {
                    check(workoutSession?.serverState == null) { "진행 중인 서버 운동을 먼저 완료해 주세요." }
                    workoutHistory = emptyList(); workoutSession = null; mateState.activeWorkoutId = null
                })
            PrototypeRoute.HISTORY -> Screen(Modifier.padding(padding)) {
                ServerRecordList(apiClient)
                if (workoutHistory.any { it.serverId == null } || runningState.history.any { it.serverId == null }) {
                    Text("기기에 보관된 이전 기록", style = MaterialTheme.typography.titleMedium)
                    WorkoutRecordBrowser(workoutHistory.filter { it.serverId == null }, runningState.history.filter { it.serverId == null }, allowExamples = false)
                }
            }
            PrototypeRoute.REPORTS -> ReportTicketsFlow(Modifier.padding(padding), socialState)
            PrototypeRoute.NOTIFICATIONS -> NotificationFlow(Modifier.padding(padding), socialState, { notice ->
                when (notice.destination) {
                    "PERSON" -> { socialState.accounts.opened = notice.target }
                    "FOLLOW" -> socialState.accounts.directory = "팔로우 요청"
                    "POST" -> {
                        socialState.localPostId(notice.target)?.let { socialState.selectedPostId = it }
                        route = PrototypeRoute.COMMUNITY
                    }
                    "DM_CONVERSATION" -> {
                        socialState.serverChat(notice.target)?.let { currentChat = it; route = PrototypeRoute.DM_DETAIL }
                            ?: run { route = PrototypeRoute.DM }
                    }
                    "MATE_POST" -> {
                        mateServerState.postId = notice.target
                        mateServerState.page = if (notice.target.isNullOrBlank()) "목록" else "상세"
                        route = PrototypeRoute.MATE_LIST
                    }
                    "MATE_CHAT_ROOM" -> {
                        mateServerState.roomId = notice.target
                        mateServerState.chatOrigin = "문의 목록"
                        mateServerState.page = if (notice.target.isNullOrBlank()) "문의 목록" else "채팅"
                        route = PrototypeRoute.MATE_LIST
                    }
                    "NOTICE" -> socialState.notice = "공지사항 예시 · 실제 운영 공지는 서버 연결 후 제공됩니다."
                    else -> route = runCatching { PrototypeRoute.valueOf(notice.destination) }.getOrDefault(PrototypeRoute.HOME)
                }
            }, serverSocial)
            PrototypeRoute.ONBOARDING -> OnboardingScreen(Modifier.padding(padding), apiClient) { updatedProfile ->
                profile = updatedProfile
                route = PrototypeRoute.HOME
            }
            PrototypeRoute.HOME -> SocialHome(Modifier.padding(padding), profile, socialState, workoutHistory.size, runningState.history.size,
                notifications = { route = PrototypeRoute.NOTIFICATIONS },
                resuming = workoutSession != null || runningState.phase != "설정",
                start = { if (workoutSession != null) route = PrototypeRoute.WORKOUT else if (runningState.phase != "설정") route = PrototypeRoute.RUNNING else { mateState.activeWorkoutId = null; route = PrototypeRoute.SOLO } },
                mates = { route = PrototypeRoute.MATE_LIST }, community = { route = PrototypeRoute.COMMUNITY },
                dm = { route = PrototypeRoute.DM }, inbody = { inbodyOrigin = PrototypeRoute.HOME; route = PrototypeRoute.INBODY }, server = serverSocial)
            PrototypeRoute.SOLO -> SoloScreen(Modifier.padding(padding), { mateState.activeWorkoutId = null; route = PrototypeRoute.HEALTH_ROUTINE }, { mateState.activeWorkoutId = null; route = PrototypeRoute.RUNNING })
            PrototypeRoute.HEALTH_ROUTINE -> if (!localRoutineMode) ServerRoutineLibrary(Modifier.padding(padding), apiClient, Sport.FITNESS in profile.interests,
                select = { routine ->
                    selectedServerRoutine = routine
                    val names = routine.rows("items").map { it.text("exerciseName") }
                    selectedWorkoutRoutine = WorkoutRoutine(routine.text("name"), names.joinToString(" → "), names.firstOrNull().orEmpty(), names)
                    route = PrototypeRoute.WORKOUT_PREP
                }, local = { localRoutineMode = true }) else RoutineLibrary(
                modifier = Modifier.padding(padding),
                saved = savedRoutines,
                update = { savedRoutines = it },
                exerciseCatalog = exerciseCatalog,
                onServer = { localRoutineMode = false }
            ) { routine ->
                selectedServerRoutine = null
                selectedWorkoutRoutine = WorkoutRoutine(routine.name, routine.exercises.joinToString(" → "), routine.exercises.firstOrNull().orEmpty(), routine.exercises)
                route = PrototypeRoute.WORKOUT_PREP
            }
            PrototypeRoute.WORKOUT_PREP -> WorkoutPrepScreen(Modifier.padding(padding), selectedWorkoutRoutine) { useSamsungHealth ->
                if (!startingWorkout) {
                    startingWorkout = true; startError = null
                    integrationScope.launch {
                        try {
                            val routine = selectedServerRoutine
                            val current = WorkoutSession(selectedWorkoutRoutine.firstExerciseName, useSamsungHealth).also {
                                it.sessionExercises.clear(); it.sessionExercises.addAll(selectedWorkoutRoutine.exercises)
                            }
                            if (routine != null) {
                                val response = withContext(Dispatchers.IO) { apiClient.request("POST", "workout-sessions", mapOf("routineId" to routine.text("id"))).asJsonObject }
                                current.serverState = response.toString()
                                current.targetSetCount = response.rows("exercises").sumOf { it.rows("sets").size }
                            }
                            workoutSession = current; route = PrototypeRoute.WORKOUT
                        } catch (failure: Exception) { startError = failure.message }
                        finally { startingWorkout = false }
                    }
                }
            }
            PrototypeRoute.RUNNING -> RunningFlow(Modifier.padding(padding), runningState, locationConsent, { locationConsent = it }, apiClient) { saved ->
                if (saved) mateState.completeWorkout() else mateState.activeWorkoutId = null
                route = PrototypeRoute.HOME
            }
            PrototypeRoute.MATE_LIST, PrototypeRoute.MATE_DETAIL, PrototypeRoute.MATE_WRITE -> ServerMateFlow(
                Modifier.padding(padding), apiClient, mateServerState, profile, serverAccount.text("id")
            ) { matePosts = it }
            PrototypeRoute.WORKOUT -> WorkoutScreen(
                modifier = Modifier.padding(padding),
                session = requireNotNull(workoutSession),
                history = workoutHistory,
                availableExercises = (exerciseCatalog.state as? ExerciseCatalogState.Ready)?.exercises.orEmpty(),
                serverWorkout = serverWorkout,
                completeServer = { summary ->
                    val current = requireNotNull(workoutSession)
                    if (current.serverState != null) withContext(Dispatchers.IO) { serverWorkout?.complete(WorkoutUpload(summary.sets, current.isTimerRunning)) }
                },
                saveRoutine = { routine -> if (savedRoutines.any { it.name == routine.name }) false else { savedRoutines = savedRoutines + routine; true } },
                onCancel = { cancelError = null; cancelWorkout = true }
            ) { summary ->
                latestServerSessionId = workoutSession?.serverState?.let { com.google.gson.JsonParser.parseString(it).asJsonObject.text("id") }
                latestWorkoutSummary = summary
                workoutHistory = listOf(summary.copy(serverId = latestServerSessionId)) + workoutHistory
                socialState.accounts.notify(socialState, "헬스 리포트가 준비됐어요.", "운동", "HISTORY")
                mateState.completeWorkout()
                workoutSession = null
                route = PrototypeRoute.REPORT
            }
            PrototypeRoute.REPORT -> if (latestServerSessionId != null) Screen(Modifier.padding(padding)) {
                ServerRecordDetail(apiClient, requireNotNull(latestServerSessionId))
                PrimaryAction("홈으로") { route = PrototypeRoute.HOME }
            } else ReportScreen(
                modifier = Modifier.padding(padding),
                summary = latestWorkoutSummary,
                previousSummary = workoutHistory.drop(1).firstOrNull(),
                samsungHealthState = samsungHealthState,
                refreshSamsungHealth = onRefreshSamsungHealth,
                openSamsungHealth = onOpenSamsungTest
            ) { route = PrototypeRoute.HOME }
            PrototypeRoute.INBODY -> if (healthConsent) InbodyFlow(Modifier.padding(padding), inbodyState, serverInbody) else Screen(Modifier.padding(padding)) {
                Text("건강정보 처리 동의가 필요해요.", style = MaterialTheme.typography.titleLarge)
                Text("인바디 수치와 사진은 민감한 정보입니다. 동의하면 서버로 전송해 기록하며, 사진 판독 시 외부 AI 서비스에서 처리될 수 있습니다. 동의하지 않아도 기본 운동 기록은 사용할 수 있어요.")
                PrimaryAction("건강정보 처리에 동의하고 계속") { healthConsent = true }
                TextButton({ route = inbodyOrigin }) { Text("나중에") }
            }
            PrototypeRoute.COMMUNITY -> CommunityFlow(Modifier.padding(padding), socialState, profile.nickname, workoutHistory, runningState.history, serverSocial, serverAccount.text("id"))
            PrototypeRoute.DM -> DmListFlow(Modifier.padding(padding), socialState, { room -> currentChat = room; route = PrototypeRoute.DM_DETAIL }, serverSocial)
            PrototypeRoute.DM_DETAIL -> currentChat?.let { room -> DmChatFlow(Modifier.padding(padding), socialState, room, { route = PrototypeRoute.DM }, serverSocial) }
            PrototypeRoute.PROFILE -> ProfileScreen(
                modifier = Modifier.padding(padding),
                profile = profile,
                social = socialState,
                workouts = workoutHistory,
                runs = runningState.history,
                api = apiClient,
                onReload = {
                    val loaded = withContext(Dispatchers.IO) { apiClient.request("GET", "users/me").asJsonObject }
                    profileVersion = loaded.getAsJsonObject("profile").get("version").asLong
                    profile = loaded.toUserProfile()
                },
                onSave = { updated ->
                    val saved = withContext(Dispatchers.IO) { apiClient.request("PATCH", "users/me/profile", profilePatch(profile, updated, profileVersion, apiClient.fetchSports().map { it.code }.toSet())).asJsonObject }
                    profileVersion = saved.getAsJsonObject("profile").get("version").asLong
                    profile = saved.toUserProfile()
                },
                onSavePhoto = { mediaId ->
                    val saved = withContext(Dispatchers.IO) { apiClient.request("PATCH", "users/me/profile", mapOf("version" to profileVersion, "profileImageMediaId" to mediaId)).asJsonObject }
                    profileVersion = saved.getAsJsonObject("profile").get("version").asLong
                    profile = saved.toUserProfile()
                    profile
                },
                onOpenInbody = { inbodyOrigin = PrototypeRoute.PROFILE; route = PrototypeRoute.INBODY },
                onSamsungTest = onOpenSamsungTest,
                onAccount = { route = PrototypeRoute.ACCOUNT },
                onHistory = { route = PrototypeRoute.HISTORY },
                onNotifications = { route = PrototypeRoute.NOTIFICATIONS },
                onReports = { route = PrototypeRoute.REPORTS },
                onOpenAdminDemo = { route = PrototypeRoute.ADMIN },
                editRequest = profileEditRequest,
                onOpenSettings = { route = PrototypeRoute.SETTINGS },
            )
            PrototypeRoute.SAFETY -> SafetyScreen(Modifier.padding(padding))
            PrototypeRoute.ADMIN -> AdminScreen(Modifier.padding(padding)) { route = PrototypeRoute.ADS }
            PrototypeRoute.ADS -> AdsScreen(Modifier.padding(padding))
        }
        if (storageError) AlertDialog(onDismissRequest = { storageError = false }, title = { Text("기기 저장 확인 필요") }, text = { Text("기록을 읽거나 저장하지 못했어요. 저장 공간을 확인해 주세요. 최근 변경은 재실행 후 복원되지 않을 수 있습니다.") }, confirmButton = { TextButton({ storageError = false }) { Text("확인") } })
        if (startingWorkout) AlertDialog(onDismissRequest = {}, title = { Text("운동 준비 중") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
        startError?.let { message -> AlertDialog(onDismissRequest = { startError = null }, title = { Text("운동을 시작하지 못했어요") }, text = { Text(message) }, confirmButton = { TextButton({ startError = null }) { Text("확인") } }) }
        }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FitMatePrototypePreview() {
    FitmateTheme(darkTheme = false) {
        FitMatePrototypeApp(onOpenSamsungTest = {})
    }
}

@Composable private fun BottomNavigation(selectedRoute: PrototypeRoute, onRoute: (PrototypeRoute) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().background(FitMateColors.Surface).padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(PrototypeRoute.HOME, "홈", Icons.Outlined.Home, selectedRoute, onRoute)
        NavItem(PrototypeRoute.MATE_LIST, "메이트", Icons.Outlined.Groups, selectedRoute, onRoute)
        NavItem(PrototypeRoute.COMMUNITY, "커뮤니티", Icons.Outlined.GridView, selectedRoute, onRoute)
        NavItem(PrototypeRoute.DM, "DM", Icons.Outlined.ChatBubbleOutline, selectedRoute, onRoute)
        NavItem(PrototypeRoute.PROFILE, "프로필", Icons.Outlined.PersonOutline, selectedRoute, onRoute)
    }
}

@Composable private fun RowScope.NavItem(
    route: PrototypeRoute,
    label: String,
    icon: ImageVector,
    selectedRoute: PrototypeRoute,
    onRoute: (PrototypeRoute) -> Unit
) {
    val selected = route == selectedRoute
    val tint = if (selected) FitMateColors.TextPrimary else FitMateColors.NeutralIcon
    Column(
        modifier = Modifier.weight(1f).selectable(selected = selected, role = Role.Tab, onClick = { onRoute(route) }).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(25.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable internal fun Screen(modifier: Modifier = Modifier, background: Color = Lavender, content: @Composable ColumnScope.() -> Unit) = LazyColumn(
    modifier.fillMaxSize().background(background).imePadding()
) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FitMateSpacing.ScreenHorizontal, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(FitMateSpacing.Item),
            content = content
        )
    }
}

@Composable internal fun SectionTitle(title: String, subtitle: String? = null) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = FitMateColors.TextPrimary); subtitle?.let { Text(it, color = FitMateColors.TextSecondary) } }
@Composable internal fun SoftCard(content: @Composable ColumnScope.() -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = FitMateColors.Surface), shape = FitMateShapes.Card, border = BorderStroke(1.dp, FitMateColors.Outline), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(FitMateSpacing.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
@Composable internal fun PrimaryAction(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).pressFeedback(interaction),
        interactionSource = interaction, shape = FitMateShapes.Button) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable private fun OnboardingScreen(modifier: Modifier, apiClient: FitMateApiClient, onHealthConsent: (Boolean) -> Unit = {}, done: (UserProfile) -> Unit) = Screen(modifier) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var referenceError by remember { mutableStateOf<String?>(null) }
    var terms by remember { mutableStateOf(emptyList<com.google.gson.JsonObject>()) }
    var activeCodes by remember { mutableStateOf(emptySet<String>()) }
    var referenceAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(referenceAttempt) {
        referenceError = null
        try {
            val refs = withContext(Dispatchers.IO) { apiClient.items("terms", false) to apiClient.fetchSports() }
            terms = refs.first; activeCodes = refs.second.map { it.code }.toSet()
        } catch (failure: Exception) { referenceError = failure.message }
    }
    var verified by remember { mutableStateOf(false) }
    var checkedNickname by remember { mutableStateOf<String?>(null) }
    var signupError by remember { mutableStateOf<String?>(null) }
    var policy by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(1) }
    var emailLocalPart by remember { mutableStateOf("") }
    var selectedEmailDomain by remember { mutableStateOf("naver.com") }
    var customEmailDomain by remember { mutableStateOf("") }
    var isEmailDomainMenuOpen by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var isAdultVerified by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var regionId by remember { mutableStateOf("") }
    var facilityName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var selectedSports by remember { mutableStateOf(setOf(Sport.FITNESS, Sport.RUNNING)) }
    var customInterests by remember { mutableStateOf(emptySet<String>()) }
    var customInterestDraft by remember { mutableStateOf("") }
    var isCustomInterestInputVisible by remember { mutableStateOf(false) }
    var experience by remember { mutableStateOf("입문") }
    var goals by remember { mutableStateOf(setOf("운동 습관 만들기")) }
    var availableTime by remember { mutableStateOf("평일 저녁") }
    var agreedToTerms by remember { mutableStateOf(false) }
    var agreedToPrivacy by remember { mutableStateOf(false) }
    var agreedToHealthData by remember { mutableStateOf(false) }
    val emailDomain = if (selectedEmailDomain == "직접 입력") customEmailDomain else selectedEmailDomain
    val email = if (emailLocalPart.isBlank() || emailDomain.isBlank()) "" else "$emailLocalPart@$emailDomain"
    Text("FITMATE", color = Mint, fontWeight = FontWeight.Bold)
    ErrorText(signupError)
    referenceError?.let { ErrorText(it); TextButton({ referenceAttempt++ }) { Text("가입 정보 다시 불러오기") } }
    policy?.let { title -> SocialSheet({ policy = null }) { PolicyDetail(title) } }
    when (step) {
        1 -> {
            SectionTitle("계정을 만들어요", "서비스 이용을 위한 기본 정보를 입력하세요.")
            Text("1 / 4  계정 정보", color = Mint)
            Text("이메일", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(emailLocalPart, { emailLocalPart = it }, Modifier.weight(1f), label = { Text("아이디") }, singleLine = true)
                Text("@", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.weight(1.25f)) {
                    OutlinedTextField(
                        value = if (selectedEmailDomain == "직접 입력") customEmailDomain else selectedEmailDomain,
                        onValueChange = { if (selectedEmailDomain == "직접 입력") customEmailDomain = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("도메인") },
                        readOnly = selectedEmailDomain != "직접 입력",
                        trailingIcon = {
                            IconButton(onClick = { isEmailDomainMenuOpen = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "도메인 선택")
                            }
                        },
                        singleLine = true
                    )
                    DropdownMenu(expanded = isEmailDomainMenuOpen, onDismissRequest = { isEmailDomainMenuOpen = false }) {
                        listOf("naver.com", "gmail.com", "daum.net", "hanmail.net", "nate.com", "직접 입력").forEach { domain ->
                            DropdownMenuItem(text = { Text(domain) }, onClick = {
                                selectedEmailDomain = domain
                                isEmailDomainMenuOpen = false
                            })
                        }
                    }
                }
            }
            Text("로그인 아이디로 사용해요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            LaunchedEffect(email) { verified = false }
            TextButton({ val submittedEmail = email; scope.launch {
                try { val available = withContext(Dispatchers.IO) { apiClient.checkEmailAvailability(submittedEmail).available }; if (email == submittedEmail) { verified = available; signupError = if (available) null else "이미 가입한 이메일입니다." } }
                catch (failure: Exception) { signupError = failure.message }
            } }, enabled = validDemoEmail(email)) { Text(if (verified) "사용 가능한 이메일" else "이메일 중복 확인") }
            SecretField(password, { password = it }, "비밀번호")
            Text("영문·숫자 포함 8~64자. 이메일 소유 인증은 아직 지원하지 않습니다.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("이름") }, singleLine = true)
            OutlinedTextField(birthDate, { birthDate = it }, Modifier.fillMaxWidth(), label = { Text("생년월일 (YYYY.MM.DD)") }, singleLine = true)
            Text("성별", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("남성", "여성", "선택 안 함").forEach { option -> FilterChip(gender == option, { gender = option }, { Text(option) }) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("만 18세 이상입니다.", style = MaterialTheme.typography.bodyMedium)
                Switch(isAdultVerified, { isAdultVerified = it })
            }
            PrimaryAction("다음") {
                val birth = runCatching { java.time.LocalDate.parse(birthDate.replace('.', '-')) }.getOrNull()
                if (verified && validDemoPassword(password) && password.length <= 64 && password.toByteArray().size <= 72 && displayName.isNotBlank() && birth != null && !birth.isAfter(java.time.LocalDate.now().minusYears(18)) && isAdultVerified) { signupError = null; step = 2 }
                else signupError = "이메일 중복 확인, 비밀번호, 생년월일과 만 18세 이상 여부를 확인하세요."
            }
        }
        2 -> {
            SectionTitle("내 프로필을 꾸며요", "다른 사용자에게는 닉네임과 대략적인 지역만 보여요.")
            Text("2 / 4  프로필", color = Mint)
            OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("닉네임") }, singleLine = true)
            TextButton({
                val value = nickname.trim()
                if (value.isBlank()) {
                    checkedNickname = null
                    signupError = "닉네임을 입력하세요."
                } else {
                    scope.launch {
                        val available = runCatching { withContext(Dispatchers.IO) { apiClient.checkNicknameAvailability(value).available } }.getOrNull()
                        checkedNickname = value.takeIf { available == true }
                        signupError = when (available) {
                            true -> null
                            false -> "이미 사용 중인 닉네임입니다."
                            null -> "닉네임 중복 확인에 실패했습니다. 서버 연결을 확인하세요."
                        }
                    }
                }
            }) { Text(if (checkedNickname == nickname.trim()) "사용 가능" else "닉네임 중복 확인") }
            ServerRegionPicker(apiClient, region) { id, name -> regionId = id; region = name }
            ServerFacilityPicker(apiClient, regionId, facilityName) { facilityName = it }
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("자기소개 (선택)") }, minLines = 2)
            PrimaryAction("다음") { if (nickname.isNotBlank() && checkedNickname == nickname.trim() && region.isNotBlank()) { signupError = null; step = 3 } else signupError = "닉네임 중복 확인과 활동 지역을 입력하세요." }
        }
        3 -> {
            SectionTitle("운동 정보를 알려주세요", "추천 루틴과 운동 메이트를 맞추는 데 사용해요.")
            Text("3 / 4  운동 프로필", color = Mint)
            Text("관심 운동 · 복수 선택", style = MaterialTheme.typography.labelLarge)
            Sport.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { sport ->
                        FilterChip(selectedSports.contains(sport), { selectedSports = if (selectedSports.contains(sport)) selectedSports - sport else selectedSports + sport }, { Text(sport.label) })
                    }
                }
            }
            if (customInterests.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    customInterests.forEach { interest -> FilterChip(true, { customInterests = customInterests - interest }, { Text("$interest ×") }) }
                }
            }
            if (isCustomInterestInputVisible) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customInterestDraft,
                        onValueChange = { customInterestDraft = it },
                        modifier = Modifier.weight(1f).height(54.dp),
                        placeholder = { Text("운동명 입력") },
                        singleLine = true,
                        shape = FitMateShapes.Button
                    )
                    Button(
                        onClick = {
                            if (customInterestDraft.isNotBlank()) {
                                customInterests = customInterests + customInterestDraft.trim()
                                customInterestDraft = ""
                                isCustomInterestInputVisible = false
                            }
                        },
                        modifier = Modifier.height(54.dp),
                        shape = FitMateShapes.Button,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                    ) { Text("추가") }
                }
            } else {
                OutlinedButton({ isCustomInterestInputVisible = true }, shape = FitMateShapes.Button) { Text("+ 직접 추가", color = Mint) }
            }
            Text("운동 경력", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("입문", "6개월 미만", "1년 이상").forEach { option -> FilterChip(experience == option, { experience = option }, { Text(option) }) }
            }
            Text("운동 목표 · 복수 선택", style = MaterialTheme.typography.labelLarge)
            Text("주 목표: ${goals.firstOrNull() ?: "목표를 선택하세요"} · 먼저 선택한 목표를 기준으로 추천해요.", style = MaterialTheme.typography.bodySmall)
            listOf("다이어트", "근육 증가", "체력 향상", "기록 향상", "운동 습관 만들기").chunked(3).forEach { goalRow ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goalRow.forEach { goal -> FilterChip(goals.contains(goal), { goals = if (goals.contains(goal)) goals - goal else goals + goal }, { Text(goal) }) }
                }
            }
            Text("주 운동 가능 시간", style = MaterialTheme.typography.labelLarge)
            Text("평일 아침 06~09시 · 평일 저녁 18~22시 · 주말 09~18시", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("평일 아침", "평일 저녁", "주말").forEach { option -> FilterChip(availableTime == option, { availableTime = option }, { Text(option) }) }
            }
            PrimaryAction("다음") { if ((selectedSports.isNotEmpty() || customInterests.isNotEmpty()) && goals.isNotEmpty()) step = 4 }
        }
        else -> {
            SectionTitle("약관과 정보를 확인해요", "필수 약관 동의 후 FitMate를 시작할 수 있어요.")
            Text("4 / 4  가입 완료", color = Mint)
            TextButton({ policy = "이용약관" }) { Text("이용약관 상세") }
            TextButton({ policy = "개인정보 처리방침" }) { Text("개인정보 처리방침 상세") }
            SoftCard {
                Text("$displayName · $nickname", fontWeight = FontWeight.Bold)
                Text("활동 지역 · $region${if (facilityName.isNotBlank()) " · $facilityName" else ""}")
                Text("관심 운동 · ${(selectedSports.map { it.label } + customInterests).joinToString()}")
                Text("운동 목표 · ${goals.joinToString()}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("이용약관 동의 (필수)"); Switch(agreedToTerms, { agreedToTerms = it }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("개인정보 수집·이용 동의 (필수)"); Switch(agreedToPrivacy, { agreedToPrivacy = it }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("건강정보 처리 동의 (선택)"); Switch(agreedToHealthData, { agreedToHealthData = it }) }
            }
            Button(onClick = {
                if (agreedToTerms && agreedToPrivacy && !submitting) {
                    submitting = true; signupError = null
                    onHealthConsent(agreedToHealthData)
                    val submittedProfile = UserProfile(
                        nickname = nickname,
                        region = region,
                        interests = selectedSports,
                        isProfileComplete = true,
                        displayName = displayName,
                        email = email,
                        birthDate = birthDate,
                        gender = gender,
                        facilityName = facilityName,
                        experience = experience,
                        customInterests = customInterests,
                        goals = goals,
                        availableTime = availableTime,
                        bio = bio,
                        isAdultVerified = isAdultVerified
                    )
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { apiClient.authenticate("auth/signup", signupPayload(submittedProfile, password, regionId,
                                terms.filter { it.get("required").asBoolean }.map { it.text("id") }, activeCodes)) }
                            password = ""; done(submittedProfile)
                        } catch (failure: Exception) { signupError = failure.message ?: "가입 요청에 실패했습니다." }
                        finally { submitting = false }
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !submitting && agreedToTerms && agreedToPrivacy && terms.isNotEmpty() && activeCodes.isNotEmpty() && regionId.isNotEmpty()) { Text(if (submitting) "가입 중…" else "FitMate 시작하기") }
        }
    }
}

@Composable private fun HomeScreen(modifier: Modifier, profile: UserProfile, matePosts: List<MatePost>, onRoute: (PrototypeRoute) -> Unit) = Screen(modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("FITMATE", color = Mint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = {}) { Icon(Icons.Outlined.NotificationsNone, contentDescription = "알림", tint = FitMateColors.TextPrimary, modifier = Modifier.size(22.dp)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("좋은 저녁이에요, ${profile.nickname}님", style = MaterialTheme.typography.headlineMedium, color = FitMateColors.TextPrimary, maxLines = 1)
        Text("${profile.region} · ${(profile.interests.map { it.label } + profile.customInterests).joinToString()}", style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
    }
    WeeklySummaryCard()
    Text("오늘의 운동", style = MaterialTheme.typography.titleLarge, color = FitMateColors.TextPrimary)
    TodayRoutineCard { onRoute(PrototypeRoute.SOLO) }
    SocialMateCard(profile.region) { onRoute(PrototypeRoute.MATE_LIST) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("지금 참여할 수 있어요", style = MaterialTheme.typography.titleLarge, color = FitMateColors.TextPrimary)
        Text("전체 보기", style = MaterialTheme.typography.labelMedium, color = Mint, modifier = Modifier.clickable { onRoute(PrototypeRoute.MATE_LIST) })
    }
    matePosts.take(2).forEach { post -> RecruitmentListItem(post) { onRoute(PrototypeRoute.MATE_LIST) } }
}

@Composable private fun WeeklySummaryCard() = SoftCard {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("이번 주 운동", style = MaterialTheme.typography.labelMedium, color = FitMateColors.TextSecondary)
            Text("2 / 3회", style = MaterialTheme.typography.titleLarge, color = FitMateColors.TextPrimary)
            ProgressBar(progress = 2f / 3f)
        }
        Spacer(Modifier.width(24.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("오늘 목표", style = MaterialTheme.typography.labelMedium, color = FitMateColors.TextSecondary)
            Text("30분+", style = MaterialTheme.typography.titleMedium, color = Mint)
        }
    }
}

@Composable private fun ProgressBar(progress: Float) {
    val displayedProgress = animatedProgress(progress)
    Box(Modifier.fillMaxWidth().height(6.dp).background(FitMateColors.Outline, CircleShape)) {
        Box(Modifier.fillMaxWidth(displayedProgress).height(6.dp).background(Mint, CircleShape))
    }
}

@Composable private fun TodayRoutineCard(onStart: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Ink),
    shape = FitMateShapes.MainCard,
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
) {
    Column(Modifier.padding(FitMateSpacing.CardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("오늘의 루틴", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8DE5D0))
        Text("전신 웨이트", style = MaterialTheme.typography.titleLarge, color = FitMateColors.OnDarkSurface)
        Text("약 60분 · 6 exercises", style = MaterialTheme.typography.bodySmall, color = FitMateColors.OnDarkSurfaceMuted)
        Button(onClick = onStart, modifier = Modifier.padding(top = 6.dp).height(40.dp), shape = FitMateShapes.Button) {
            Text("운동 시작", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable private fun SocialMateCard(region: String, onFindMate: () -> Unit) = SoftCard {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Avatar("민", FitMateColors.PrimaryContainer, Mint, size = 28.dp)
            Avatar("지", Color(0xFFF0EAFE), Color(0xFF7459A5), size = 28.dp)
            Avatar("도", Color(0xFFFFEFE2), Color(0xFFB86618), size = 28.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("같이 운동하기", style = MaterialTheme.typography.titleMedium)
                Text("${region} · 메이트 3명 운동 중", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onFindMate, shape = FitMateShapes.Button, border = BorderStroke(1.dp, Mint)) { Text("찾기", style = MaterialTheme.typography.labelMedium, color = Mint) }
    }
}

@Composable private fun RecruitmentListItem(post: MatePost, onJoin: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = FitMateColors.Surface),
    shape = FitMateShapes.Card,
    border = BorderStroke(1.dp, FitMateColors.Outline),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
) {
    Row(Modifier.padding(FitMateSpacing.CompactPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(post.authorName.take(1), MintSoft, Mint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text("${post.dateTime} · ${post.participantCount}/${post.capacity}명", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            Text("${post.region} · ${post.difficulty}", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        }
        OutlinedButton(onClick = onJoin, shape = FitMateShapes.Button, border = BorderStroke(1.dp, Mint), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("참여", style = MaterialTheme.typography.labelMedium, color = Mint) }
    }
}

@Composable private fun Avatar(initial: String, background: Color, foreground: Color, size: androidx.compose.ui.unit.Dp = 34.dp) = Box(
    modifier = Modifier.size(size).background(background, CircleShape),
    contentAlignment = Alignment.Center
) { Text(initial, style = MaterialTheme.typography.labelMedium, color = foreground, fontWeight = FontWeight.Bold) }
@Composable private fun Shortcut(label: String, action: () -> Unit) = OutlinedButton(action) { Text(label) }

@Composable private fun SoloScreen(modifier: Modifier, start: () -> Unit, running: () -> Unit) = Screen(modifier) {
    Text("오늘 할 운동을 선택해 시작하세요.", color = FitMateColors.TextSecondary)
    SoftCard {
        Text("헬스", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("운동을 기록하고 세트별 중량과 반복 횟수를 관리해요.", style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
        WorkoutAction("헬스 시작") { start() }
    }
    SoftCard {
        Text("러닝", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("거리·시간·페이스 목표와 GPS 기록을 설정해요.", style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
        OutlinedButton(running, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("러닝 설정", color = Mint) }
    }
}

@Composable private fun HealthRoutineScreen(
    modifier: Modifier,
    routines: List<WorkoutRoutine>,
    selectRoutine: (WorkoutRoutine) -> Unit
) = Screen(modifier) {
    Text("오늘의 운동 방식에 맞는 루틴을 선택하세요.", color = FitMateColors.TextSecondary)
    routines.forEachIndexed { index, routine ->
        SoftCard {
            Text(
                when (index) {
                    0 -> "추천 루틴"
                    1 -> "저장한 루틴"
                    else -> "직접 구성"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Mint
            )
            Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(routine.description, style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
            OutlinedButton({ selectRoutine(routine) }, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("이 루틴 선택", color = Mint) }
        }
    }
}

@Composable private fun WorkoutPrepScreen(
    modifier: Modifier,
    routine: WorkoutRoutine,
    startWorkout: (Boolean) -> Unit
) = Screen(modifier) {
    var useSamsungHealth by remember { mutableStateOf(false) }
    Text("루틴과 Samsung Health Data 사용 여부를 확인하세요.", color = FitMateColors.TextSecondary)
    SoftCard {
        Text("선택한 루틴", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(routine.name, style = MaterialTheme.typography.titleLarge, color = Mint)
        Text(routine.description, style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
    }
    SoftCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Samsung Health Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("이번 운동에서만 선택적으로 사용합니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            }
            Switch(useSamsungHealth, { useSamsungHealth = it })
        }
        Text(if (useSamsungHealth) "연결·권한·동기화 가능 상태는 운동 시작 전에 확인합니다." else "FitMate 직접 기록만으로 기본 리포트를 만들 수 있어요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    }
    SoftCard {
        Text("시작 전 안내", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("가벼운 워밍업과 스트레칭 후 시작하세요. 통증이 느껴지면 운동을 중단하고 휴식하세요.", style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
    }
    WorkoutAction("운동 시작") { startWorkout(useSamsungHealth) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MateWriteScreen(modifier: Modifier, api: FitMateApiClient, initialRegionId: String, initialRegion: String, initialPost: com.google.gson.JsonObject? = null, done: suspend (RecruitmentDraft, String, String) -> Unit) = Screen(modifier) {
    val original = remember(initialPost) { initialPost?.let { com.fitmate.app.data.remote.toMatePost(it) } }
    val originalStart = remember(initialPost) { initialPost?.get("scheduledStartAt")?.asString?.let { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()) } }
    val originalMinutes = initialPost?.get("durationSeconds")?.takeUnless { it.isJsonNull }?.asInt?.div(60)
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var regionId by remember { mutableStateOf(initialRegionId) }
    var region by remember { mutableStateOf(initialRegion) }
    var attempt by remember { mutableStateOf<Pair<RecruitmentDraft, String>?>(null) }
    var attemptedRegion by remember { mutableStateOf("") }
    var idempotencyKey by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var selectedSport by remember { mutableStateOf(original?.sport ?: Sport.FITNESS) }
    var activeSports by remember { mutableStateOf(emptyList<Sport>()) }
    var sportLoadError by remember { mutableStateOf<String?>(null) }
    var sportReload by remember { mutableStateOf(0) }
    LaunchedEffect(api, sportReload) {
        sportLoadError = null
        try {
            val codes = withContext(Dispatchers.IO) { api.fetchSports().map { it.code }.toSet() }
            activeSports = Sport.entries.filter { it.apiCode() in codes }
            if (selectedSport !in activeSports && activeSports.isNotEmpty()) selectedSport = activeSports.first()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { sportLoadError = e.message ?: "운동 종목을 불러오지 못했습니다." }
    }
    var details by remember { mutableStateOf(original?.details ?: MateDetails(sportFields = mateFields(selectedSport).mapValues { it.value.firstOrNull().orEmpty() })) }
    var selectedDate by remember { mutableStateOf(originalStart?.toLocalDate()?.toString() ?: "오늘") }
    var startHour by remember { mutableStateOf(originalStart?.hour ?: 19) }
    var startMinute by remember { mutableStateOf(originalStart?.minute ?: 30) }
    var durationHour by remember { mutableStateOf(originalMinutes?.div(60) ?: 1) }
    var durationMinute by remember { mutableStateOf(originalMinutes?.rem(60) ?: 30) }
    var timePickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }
    var capacityText by remember { mutableStateOf(original?.capacity?.toString() ?: "2") }
    var difficulty by remember { mutableStateOf(when (initialPost?.get("difficulty")?.asString) { "BEGINNER" -> "초보"; "ADVANCED" -> "중급+"; else -> "초중급" }) }
    var description by remember { mutableStateOf(original?.description.orEmpty()) }
    var isBeginnerWelcome by remember { mutableStateOf(original?.isBeginnerWelcome ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val selectedStartTime = "%02d:%02d".format(startHour, startMinute)
    val selectedDuration = when {
        durationHour > 0 && durationMinute > 0 -> "${durationHour}시간 ${durationMinute}분"
        durationHour > 0 -> "${durationHour}시간"
        else -> "${durationMinute}분"
    }

    timePickerTarget?.let { target ->
        val pickerState = rememberTimePickerState(
            initialHour = if (target == TimePickerTarget.START_TIME) startHour else durationHour,
            initialMinute = if (target == TimePickerTarget.START_TIME) startMinute else durationMinute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text(if (target == TimePickerTarget.START_TIME) "시작 시간 선택" else "예상 시간 선택") },
            confirmButton = {
                Button(onClick = {
                    if (target == TimePickerTarget.START_TIME) {
                        startHour = pickerState.hour
                        startMinute = pickerState.minute
                    } else {
                        durationHour = pickerState.hour
                        durationMinute = pickerState.minute
                    }
                    timePickerTarget = null
                }) { Text("확인") }
            },
            dismissButton = { OutlinedButton(onClick = { timePickerTarget = null }) { Text("취소") } }
        ) { TimePicker(state = pickerState) }
    }

    Text("카테고리에 맞는 조건을 입력하세요.", color = FitMateColors.TextSecondary)
    ErrorText(sportLoadError)
    if (sportLoadError != null) TextButton({ sportReload++ }) { Text("운동 종목 다시 불러오기") }
    ServerRegionPicker(api, region) { id, label -> regionId = id; region = label }
    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("모집 제목") }, supportingText = { Text("예: 오늘 저녁 성수동에서 하체 운동 같이 해요") })
    SoftCard {
        Text("운동 종목", fontWeight = FontWeight.Bold)
        activeSports.chunked(3).forEach { sports ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sports.forEach { sport -> FilterChip(selectedSport == sport, {
                    if (selectedSport != sport) details = details.copy(sportFields = mateFields(sport).mapValues { it.value.firstOrNull().orEmpty() })
                    selectedSport = sport
                }, { Text(sport.label) }) }
            }
        }
    }
    SoftCard {
        Text("모집 조건", fontWeight = FontWeight.Bold)
        Text("운동 날짜", style = MaterialTheme.typography.labelLarge)
        if (originalStart != null) FilterChip(selectedDate == originalStart.toLocalDate().toString(), { selectedDate = originalStart.toLocalDate().toString() }, { Text(originalStart.toLocalDate().toString()) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("오늘", "내일", "토요일", "일요일").forEach { option -> FilterChip(selectedDate == option, { selectedDate = option }, { Text(option) }) }
        }
        Text("시작 시간", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { timePickerTarget = TimePickerTarget.START_TIME }, modifier = Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text(selectedStartTime) }
        Text("예상 시간", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { timePickerTarget = TimePickerTarget.DURATION }, modifier = Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text(selectedDuration) }
        OutlinedTextField(capacityText, { capacityText = it }, Modifier.fillMaxWidth(), label = { Text("모집 인원") }, supportingText = { Text("본인을 포함한 전체 인원") }, singleLine = true)
        Text("난이도", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("초보", "초중급", "중급+").forEach { option -> FilterChip(difficulty == option, { difficulty = option }, { Text(option) }) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("초보자 환영", style = MaterialTheme.typography.titleSmall); Text("처음 참여하는 메이트도 신청할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary) }
            Switch(isBeginnerWelcome, { isBeginnerWelcome = it })
        }
    }
    SoftCard {
        Text("상세 안내", fontWeight = FontWeight.Bold)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("운동 내용과 준비물을 알려주세요") }, minLines = 3)
        Text("정확한 집결 위치는 참가 확정자에게만 공개됩니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    }
    MateDetailsEditor(selectedSport, details) { details = it }
    validationError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (submitting) LinearProgressIndicator(Modifier.fillMaxWidth())
    PrimaryAction(if (submitting) "저장 중…" else if (initialPost != null) "수정 저장" else "모집 게시") {
        if (submitting) return@PrimaryAction
        val capacity = capacityText.toIntOrNull()
        validationError = when {
            selectedSport !in activeSports -> "서버에서 사용할 수 있는 운동 종목을 불러온 뒤 선택하세요."
            regionId.isBlank() -> "활동 지역을 선택하세요."
            title.isBlank() -> "모집 제목을 입력하세요."
            capacity == null || capacity !in 2..100 -> "모집 인원은 2명에서 100명 사이로 입력하세요."
            description.isBlank() -> "운동 내용 또는 준비물을 입력하세요."
            durationHour == 0 && durationMinute == 0 -> "예상 시간은 1분 이상이어야 합니다."
            details.location.isBlank() || details.meetingPoint.isBlank() -> "공개 장소와 확정자 집결 위치를 입력하세요."
            details.cost.toLongOrNull()?.let { it in 0..10000000 } != true -> "예상 비용은 0~10,000,000원으로 입력하세요."
            else -> null
        }
        if (validationError == null && capacity != null) {
            val draft = RecruitmentDraft(title, selectedSport, "$selectedDate $selectedStartTime", selectedDuration, capacity, difficulty, description, isBeginnerWelcome, details)
            if (attempt?.first != draft || attemptedRegion != regionId) idempotencyKey = java.util.UUID.randomUUID().toString()
            attempt = draft to idempotencyKey; attemptedRegion = regionId
            val submittedRegion = regionId
            submitting = true
            scope.launch {
                try { done(draft, submittedRegion, idempotencyKey) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { validationError = e.message ?: "모집 글을 저장하지 못했습니다." }
                finally { submitting = false }
            }
        }
    }
}

private enum class TimePickerTarget { START_TIME, DURATION }

@Composable private fun WorkoutScreen(
    modifier: Modifier,
    session: WorkoutSession,
    history: List<WorkoutSummary>,
    availableExercises: List<ApiExercise>,
    serverWorkout: ServerWorkout?,
    completeServer: suspend (WorkoutSummary) -> Unit,
    saveRoutine: (SavedRoutine) -> Boolean,
    onCancel: () -> Unit,
    finish: (WorkoutSummary) -> Unit
) = with(session) { Screen(modifier) {
    val uploadScope = rememberCoroutineScope()
    var completing by remember { mutableStateOf(false) }
    var compositionSaving by remember { mutableStateOf(false) }
    var catalogExpanded by remember { mutableStateOf(false) }
    var selectedServerExercise by remember { mutableStateOf<ApiExercise?>(null) }
    fun applyServerComposition(response: com.google.gson.JsonObject) {
        val names = response.rows("exercises").map { it.text("exerciseName") }
        val selectedName = sessionExercises.getOrNull(selectedExerciseIndex)
        sessionExercises.clear()
        sessionExercises.addAll(names)
        selectedExerciseIndex = names.indexOf(selectedName).takeIf { it >= 0 } ?: 0
        exerciseName = names.getOrElse(selectedExerciseIndex) { "" }
    }
    fun editServerComposition(action: () -> com.google.gson.JsonObject) {
        if (compositionSaving) return
        compositionSaving = true
        syncError = null
        uploadScope.launch {
            try {
                applyServerComposition(withContext(Dispatchers.IO) { action() })
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                syncError = failure.message ?: "운동 구성을 저장하지 못했습니다."
            } finally { compositionSaving = false }
        }
    }
    if (serverState != null) {
        Text("운동 구성과 세트 기록을 서버에 저장합니다.")
        syncError?.let { ErrorText(it) }
    } else Text("직접 구성 운동은 이 기기에 저장됩니다.")
    var recordEvent by remember(session) { mutableStateOf(0) }
    var highlightedSet by remember(session) { mutableStateOf(-1) }
    var recordConfirmation by remember(session) { mutableStateOf<String?>(null) }
    val highlight = recordHighlight(recordEvent)
    LaunchedEffect(recordEvent) {
        if (recordEvent > 0) {
            delay(1400)
            recordConfirmation = null
            highlightedSet = -1
        }
    }
    val currentVolume = recordedSets.sumOf { it.weightKg * it.repetitions }
    val elapsedTime = "%02d:%02d:%02d".format(elapsedSeconds / 3600, elapsedSeconds / 60 % 60, elapsedSeconds % 60)
    val displayedRestSeconds = if (remainingRestSeconds > 0) remainingRestSeconds else restPresetSeconds

    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }
    LaunchedEffect(isRestTimerRunning) {
        while (isRestTimerRunning) {
            delay(1_000)
            if (remainingRestSeconds > 1) {
                remainingRestSeconds -= 1
            } else {
                remainingRestSeconds = 0
                isRestTimerRunning = false
            }
        }
    }

    SoftCard {
        Text("Samsung Health Data", fontWeight = FontWeight.Bold)
        Text(if (useSamsung) "기기에서 먼저 운동 시작 → FitMate 기록 → 기기 종료 후 상세 로그 동기화" else "FitMate 직접 기록으로 기본 리포트를 생성합니다.")
    }
    SoftCard {
        Text("오늘의 세트 목표", fontWeight = FontWeight.Bold)
        Text("완료한 세트 수를 기준으로 리포트에서 달성 여부를 보여드려요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 6, 9).forEach { count ->
                FilterChip(
                    selected = targetSetCount == count,
                    onClick = { targetSetCount = count },
                    label = { Text("${count}세트") }
                )
            }
        }
    }
    SoftCard {
        Text("운동 구성", fontWeight = FontWeight.Bold)
        ExerciseHelp(session, history, saveRoutine)
        if (sessionExercises.isEmpty()) Text("첫 운동을 추가한 뒤 세트를 기록하세요.", color = Mint)
        Text("운동을 추가하고 순서를 바꾸거나 대체 운동으로 변경할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        val serverExercises = serverState?.let { com.google.gson.JsonParser.parseString(it).asJsonObject.rows("exercises") }.orEmpty()
        sessionExercises.forEachIndexed { index, name ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}. $name", modifier = Modifier.weight(1f), color = if (selectedExerciseIndex == index) Mint else FitMateColors.TextPrimary, fontWeight = if (selectedExerciseIndex == index) FontWeight.Bold else FontWeight.Normal)
                Text("선택", color = Mint, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { selectedExerciseIndex = index; exerciseName = name })
                Text("↑", color = if (index > 0) Mint else FitMateColors.TextSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable {
                    if (index > 0) {
                        if (serverState != null) {
                            val row = serverExercises.getOrNull(index) ?: return@clickable
                            editServerComposition { requireNotNull(serverWorkout).moveExercise(row.text("id"), row.get("version").asLong, index) }
                            return@clickable
                        }
                        val previous = sessionExercises[index - 1]
                        sessionExercises[index - 1] = name
                        sessionExercises[index] = previous
                        if (selectedExerciseIndex == index) selectedExerciseIndex -= 1 else if (selectedExerciseIndex == index - 1) selectedExerciseIndex += 1
                    }
                })
                Text("↓", color = if (index < sessionExercises.lastIndex) Mint else FitMateColors.TextSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable {
                    if (index < sessionExercises.lastIndex) {
                        if (serverState != null) {
                            val row = serverExercises.getOrNull(index) ?: return@clickable
                            editServerComposition { requireNotNull(serverWorkout).moveExercise(row.text("id"), row.get("version").asLong, index + 2) }
                            return@clickable
                        }
                        val next = sessionExercises[index + 1]
                        sessionExercises[index + 1] = name
                        sessionExercises[index] = next
                        if (selectedExerciseIndex == index) selectedExerciseIndex += 1 else if (selectedExerciseIndex == index + 1) selectedExerciseIndex -= 1
                    }
                })
                Text("삭제", color = if (sessionExercises.size > 1) MaterialTheme.colorScheme.error else FitMateColors.TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable {
                    if (sessionExercises.size > 1) {
                        if (recordedSets.any { it.exerciseName == name }) {
                            syncError = "세트가 기록된 운동은 삭제할 수 없습니다. 세트를 먼저 삭제하거나 운동을 완료해 주세요."
                            return@clickable
                        }
                        if (serverState != null) {
                            val row = serverExercises.getOrNull(index) ?: return@clickable
                            editServerComposition { requireNotNull(serverWorkout).deleteExercise(row.text("id"), row.get("version").asLong) }
                            return@clickable
                        }
                        sessionExercises.removeAt(index)
                        selectedExerciseIndex = selectedExerciseIndex.coerceAtMost(sessionExercises.lastIndex)
                        exerciseName = sessionExercises[selectedExerciseIndex]
                    }
                })
            }
        }
        OutlinedTextField(exerciseDraft, { next ->
            if (next != selectedServerExercise?.name) selectedServerExercise = null
            exerciseDraft = next
        }, Modifier.fillMaxWidth(), label = { Text("추가하거나 대체할 운동명") }, singleLine = true)
        if (serverState != null) Box {
            OutlinedButton({ catalogExpanded = true }, enabled = !compositionSaving, shape = FitMateShapes.Button) { Text("운동 목록에서 선택") }
            DropdownMenu(catalogExpanded, { catalogExpanded = false }) {
                availableExercises.filter { it.name.contains(exerciseDraft, ignoreCase = true) }.take(30).forEach { candidate ->
                    DropdownMenuItem(text = { Text(candidate.name) }, onClick = {
                        selectedServerExercise = candidate
                        exerciseDraft = candidate.name
                        catalogExpanded = false
                    })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({
                if (exerciseDraft.isNotBlank()) {
                    if (serverState != null) {
                        val candidate = selectedServerExercise
                        if (candidate == null || candidate.name != exerciseDraft) {
                            syncError = "서버 운동 목록에서 운동을 선택해 주세요."
                        } else if (sessionExercises.contains(candidate.name)) {
                            syncError = "같은 운동은 한 번만 추가할 수 있어요."
                        } else {
                            editServerComposition { requireNotNull(serverWorkout).addExercise(candidate.id, sessionExercises.size + 1) }
                            exerciseDraft = ""; selectedServerExercise = null
                        }
                        return@OutlinedButton
                    }
                    sessionExercises += exerciseDraft
                    selectedExerciseIndex = sessionExercises.lastIndex
                    exerciseName = exerciseDraft
                    exerciseDraft = ""
                }
            }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("운동 추가", color = Mint) }
            OutlinedButton({
                if (exerciseDraft.isNotBlank() && sessionExercises.isNotEmpty()) {
                    if (recordedSets.any { it.exerciseName == sessionExercises[selectedExerciseIndex] }) {
                        syncError = "세트가 기록된 운동은 대체할 수 없습니다."
                        return@OutlinedButton
                    }
                    if (serverState != null) {
                        val candidate = selectedServerExercise
                        val row = serverExercises.getOrNull(selectedExerciseIndex)
                        if (candidate == null || candidate.name != exerciseDraft) {
                            syncError = "서버 운동 목록에서 운동을 선택해 주세요."
                        } else if (sessionExercises.withIndex().any { it.index != selectedExerciseIndex && it.value == candidate.name }) {
                            syncError = "같은 운동으로 대체할 수 없어요."
                        } else if (row != null) {
                            editServerComposition { requireNotNull(serverWorkout).replaceExercise(row.text("id"), row.get("version").asLong, candidate.id) }
                            exerciseDraft = ""; selectedServerExercise = null
                        }
                        return@OutlinedButton
                    }
                    sessionExercises[selectedExerciseIndex] = exerciseDraft
                    exerciseName = exerciseDraft
                    exerciseDraft = ""
                }
            }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("선택 운동 대체", color = Mint) }
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("운동 중", color = Color(0xFF6FF1D0), fontWeight = FontWeight.Bold)
            Text(elapsedTime, style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text("기록 ${recordedSets.size}세트 · 총 볼륨 ${"%.0f".format(currentVolume)} kg", color = Color(0xFFD3E4DF))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ isTimerRunning = !isTimerRunning }, shape = FitMateShapes.Button) { Text(if (isTimerRunning) "일시정지" else "재개", color = Color.White) }
                OutlinedButton({ elapsedSeconds = 0 }, shape = FitMateShapes.Button) { Text("초기화", color = Color.White) }
            }
        }
    }
    SoftCard {
        Text("세트 기록", fontWeight = FontWeight.Bold)
        Text("선택 운동 · $exerciseName", style = MaterialTheme.typography.titleMedium, color = Mint)
        Choices(listOf("kg", "lb"), unit) { changeUnit(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weightText, { weightText = it }, Modifier.weight(1f), label = { Text("중량 ($unit)") }, singleLine = true)
            OutlinedTextField(repetitionText, { repetitionText = it }, Modifier.weight(1f), label = { Text("반복 횟수") }, singleLine = true)
        }
        OutlinedTextField(rpeText, { rpeText = it }, Modifier.fillMaxWidth(), label = { Text("RPE (선택 · 1~10)") }, supportingText = { Text("운동 강도와 힘든 정도를 기록하세요.") }, singleLine = true)
        OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth(), label = { Text("세트 메모 (선택)") }, minLines = 2)
        SetRecordAction(if (editingSetIndex >= 0) "세트 수정" else "이 세트 기록", recordConfirmation) {
            val wasEditing = editingSetIndex in recordedSets.indices
            val index = if (wasEditing) editingSetIndex else recordedSets.size
            if (recordSet()) {
                highlightedSet = index
                recordConfirmation = "✓ ${index + 1}세트 ${if (wasEditing) "수정" else "기록"} 완료"
                recordEvent += 1
            } else {
                recordConfirmation = null
                highlightedSet = -1
            }
        }
        ErrorText(error)
    }
    SoftCard {
        Text("휴식 타이머", fontWeight = FontWeight.Bold)
        Text("${if (remainingRestSeconds > 0) "휴식 중" else "다음 휴식"} · ${displayedRestSeconds / 60}:${(displayedRestSeconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to "30초", 60 to "1분", 90 to "1분 30초").forEach { (seconds, label) ->
                OutlinedButton({ restPresetSeconds = seconds; remainingRestSeconds = 0; isRestTimerRunning = false }, Modifier.weight(1f), shape = FitMateShapes.Button, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text(label, maxLines = 1) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({
                if (isRestTimerRunning) isRestTimerRunning = false
                else {
                    if (remainingRestSeconds == 0) remainingRestSeconds = restPresetSeconds
                    isRestTimerRunning = true
                }
            }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text(if (isRestTimerRunning) "일시정지" else if (remainingRestSeconds > 0) "재개" else "휴식 시작") }
            OutlinedButton({ remainingRestSeconds = 0; isRestTimerRunning = false }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("휴식 종료") }
        }
    }
    if (recordedSets.isNotEmpty()) {
        SoftCard {
            Text("기록한 세트", fontWeight = FontWeight.Bold)
            recordedSets.forEachIndexed { index, set ->
                Row(Modifier.fillMaxWidth().background(Mint.copy(alpha = if (highlightedSet == index) highlight * .16f else 0f), FitMateShapes.Button), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}세트 · ${set.exerciseName} · ${set.weightKg} kg × ${set.repetitions}회", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton({
                        recordConfirmation = null
                        highlightedSet = -1
                        exerciseName = set.exerciseName
                        weightText = (if (unit == "lb") set.weightKg * 2.2046226218 else set.weightKg).toString()
                        repetitionText = set.repetitions.toString()
                        rpeText = set.rpe?.toString().orEmpty()
                        memo = set.memo
                        editingSetIndex = index
                    }, shape = FitMateShapes.Button) { Text("수정") }
                    OutlinedButton({
                        recordConfirmation = null
                        highlightedSet = -1
                        recordedSets.removeAt(index)
                        if (editingSetIndex == index) editingSetIndex = -1
                        else if (editingSetIndex > index) editingSetIndex -= 1
                    }, shape = FitMateShapes.Button) { Text("삭제") }
                }
            }
        }
    }
    OutlinedButton({ isSafetyNoticeVisible = !isSafetyNoticeVisible }, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("통증·불편 시 운동 중단", color = Mint) }
    if (isSafetyNoticeVisible) {
        SoftCard {
            Text("안전 안내", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("날카로운 통증, 어지러움, 호흡 곤란이 느껴지면 즉시 운동을 멈추고 필요한 도움을 받으세요.", style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
            OutlinedButton(onCancel, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("운동 취소하고 홈으로", color = Mint) }
        }
    }
    Button({
        val summary = WorkoutSummary(elapsedSeconds / 60, recordedSets.toList(), useSamsung, targetSetCount, System.currentTimeMillis())
        completing = true
        uploadScope.launch {
            try { completeServer(summary); finish(summary) }
            catch (failure: Exception) { syncError = failure.message; error = failure.message }
            finally { completing = false }
        }
    }, Modifier.fillMaxWidth(), enabled = !completing) { Text(if (completing) "기록 저장 중…" else "운동 종료하고 리포트 보기") }
    if (completing) AlertDialog(onDismissRequest = {}, title = { Text("운동 기록을 저장하고 있어요") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
} }

@Composable private fun ReportScreen(
    modifier: Modifier,
    summary: WorkoutSummary,
    previousSummary: WorkoutSummary?,
    samsungHealthState: SamsungHealthUiState,
    refreshSamsungHealth: () -> Unit,
    openSamsungHealth: () -> Unit,
    home: () -> Unit
) = Screen(modifier) {
    val isEmpty = summary.sets.isEmpty()
    val exerciseSummaries = summary.sets.groupBy { it.exerciseName }
    val maxExerciseVolume = exerciseSummaries.values.maxOfOrNull { sets -> sets.sumOf { it.weightKg * it.repetitions } } ?: 0.0
    val totalRepetitions = summary.sets.sumOf { it.repetitions }
    val remainingGoalSets = (summary.targetSetCount - summary.sets.size).coerceAtLeast(0)
    val isGoalAchieved = summary.targetSetCount > 0 && remainingGoalSets == 0
    Text("오늘 · 헬스 · 개인 기록", color = FitMateColors.TextSecondary)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FitMateColors.WorkoutAction), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("오늘도 완료했어요! ✓", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${summary.sets.size}세트", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(if (isEmpty) "다음 운동부터 세트를 기록해 보세요." else "${summary.durationMinutes}분 · 총 반복 ${totalRepetitions}회 · 운동 ${summary.sets.map { it.exerciseName }.distinct().size}종", color = Color.White)
        }
    }
    if (!isEmpty) {
        SoftCard {
            Text("오늘의 성과", fontWeight = FontWeight.Bold)
            Text("총 ${summary.sets.size}세트 · ${totalRepetitions}회 · ${"%.0f".format(summary.totalVolumeKg)} kg", style = MaterialTheme.typography.titleMedium)
            Text(
                if (isGoalAchieved) "세트 목표 ${summary.targetSetCount}세트를 달성했어요!"
                else "세트 목표 ${summary.targetSetCount}세트까지 ${remainingGoalSets}세트 남았어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isGoalAchieved) Mint else FitMateColors.TextSecondary
            )
        }
        SoftCard {
            Text("운동별 요약", fontWeight = FontWeight.Bold)
            exerciseSummaries.forEach { (exerciseName, sets) ->
                val volume = sets.sumOf { it.weightKg * it.repetitions }
                Text("$exerciseName · ${sets.size}세트 · ${"%.0f".format(volume)} kg", style = MaterialTheme.typography.bodyMedium)
                ProgressBar(if (maxExerciseVolume > 0) (volume / maxExerciseVolume).toFloat() else 0f)
            }
        }
    }
    SoftCard {
        Text("기본 리포트", fontWeight = FontWeight.Bold)
        if (isEmpty) Text("기록된 수동 세트가 없습니다.") else summary.sets.forEach { set ->
            Text("${set.exerciseName} · ${set.weightKg} kg × ${set.repetitions}회${set.rpe?.let { " · RPE $it" }.orEmpty()}")
            if (set.memo.isNotBlank()) Text("메모 · ${set.memo}", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        }
    }
    previousSummary?.let { previous ->
        val volumeDifference = summary.totalVolumeKg - previous.totalVolumeKg
        val setDifference = summary.sets.size - previous.sets.size
        SoftCard {
            Text("이전 운동 대비", fontWeight = FontWeight.Bold)
            Text("총 볼륨 ${"%+.0f".format(volumeDifference)} kg · 세트 ${if (setDifference >= 0) "+" else ""}${setDifference}개", style = MaterialTheme.typography.bodyMedium, color = Mint)
            Text("이전 기록 ${previous.durationMinutes}분 · ${"%.0f".format(previous.totalVolumeKg)} kg", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        }
    } ?: SoftCard {
        Text("이전 운동 대비", fontWeight = FontWeight.Bold)
        Text("비교할 이전 운동 기록이 아직 없어요. 다음 운동부터 변화가 표시됩니다.", style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
    }
    if (summary.usedSamsungHealth) {
        SoftCard {
            Text("Samsung Health 상세 리포트", fontWeight = FontWeight.Bold)
            val workout = samsungHealthState.workouts.maxByOrNull { it.endTime }
            when (samsungHealthState.status) {
                SamsungHealthStatus.LOADING -> Text("상태 · 동기화 중", style = MaterialTheme.typography.bodyMedium, color = Mint)
                SamsungHealthStatus.PERMISSION_MISSING -> Text("상태 · 읽기 권한 필요", style = MaterialTheme.typography.bodyMedium, color = Mint)
                SamsungHealthStatus.DATA_EMPTY -> Text("상태 · 최근 7일 운동 기록 없음", style = MaterialTheme.typography.bodyMedium, color = Mint)
                SamsungHealthStatus.READY -> Text(if (workout == null) "상태 · 불러온 운동 기록 없음" else "상태 · 최근 동기화 완료", style = MaterialTheme.typography.bodyMedium, color = Mint)
                else -> Text("상태 · ${samsungHealthState.message ?: "연결 확인 필요"}", style = MaterialTheme.typography.bodyMedium, color = Mint)
            }
            if (workout != null) {
                val durationSeconds = java.time.Duration.between(workout.startTime, workout.endTime).seconds
                val distance = SamsungWorkoutMetrics.kilometers(workout.distanceMeters)?.let { "%.2f km".format(it) } ?: "거리 없음"
                val calories = workout.calories?.let { " · %.0f kcal".format(it) }.orEmpty()
                val heartRate = workout.meanHeartRate?.let { "평균 심박 %.0f bpm".format(it) } ?: "평균 심박 없음"
                Text("${workout.customTitle ?: workout.exerciseType} · ${durationSeconds / 60}분 · $distance$calories", style = MaterialTheme.typography.bodyMedium)
                Text(heartRate, style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            } else {
                Text("Samsung Health에 연결한 뒤 최근 7일의 제공 가능한 운동 값을 이곳에서 확인할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = refreshSamsungHealth) { Text("동기화 새로고침") }
                TextButton(onClick = openSamsungHealth) { Text("연결 관리") }
            }
            Text("이번 세션과 Samsung Health 원본 기록은 종료 시각을 기준으로 참고 표시합니다. 서버 연결 전에는 자동으로 하나의 기록으로 합치지 않습니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        }
    } else {
        SoftCard { Text("Samsung Health 미사용", fontWeight = FontWeight.Bold); Text("이번 리포트는 FitMate에 직접 기록한 운동 정보만으로 구성됐어요.") }
    }
    SoftCard { Text("마무리", fontWeight = FontWeight.Bold); Text("컨디션과 목표에 맞게 가벼운 유산소·스트레칭을 선택하고 충분히 쉬세요. 통증이 있다면 추가 운동을 중단하세요.") }
    PrimaryAction("홈으로") { home() }
}

@Composable private fun ProfileScreen(
    modifier: Modifier,
    profile: UserProfile,
    social: SocialState,
    workouts: List<WorkoutSummary>,
    runs: List<RunResult>,
    api: FitMateApiClient,
    onReload: suspend () -> Unit,
    onSave: suspend (UserProfile) -> Unit,
    onSavePhoto: suspend (String) -> UserProfile,
    onOpenInbody: () -> Unit,
    onSamsungTest: () -> Unit,
    onAccount: () -> Unit,
    onHistory: () -> Unit,
    onNotifications: () -> Unit,
    onReports: () -> Unit,
    onOpenAdminDemo: () -> Unit,
    editRequest: Int,
    onOpenSettings: () -> Unit,
) = Screen(modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val serverSocial = remember(api) { ServerSocialApi(api) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var nickname by remember(profile) { mutableStateOf(profile.nickname) }
    var region by remember(profile) { mutableStateOf(profile.region) }
    var regionId by remember(profile) { mutableStateOf(profile.activityRegionId) }
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var facilityName by remember(profile) { mutableStateOf(profile.facilityName) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var interests by remember(profile) { mutableStateOf(profile.interests) }
    var customInterests by remember(profile) { mutableStateOf(profile.customInterests) }
    var customInterestDraft by remember(profile) { mutableStateOf("") }
    var experience by remember(profile) { mutableStateOf(profile.experience) }
    var goals by remember(profile) { mutableStateOf(profile.goals) }
    var availableTime by remember(profile) { mutableStateOf(profile.availableTime) }
    var isPrivate by remember(profile) { mutableStateOf(profile.isPrivate) }
    LaunchedEffect(editRequest) { if (editRequest > 0) isEditing = true }

    if (isEditing) {
        SectionTitle("프로필 수정", "내 운동 정보와 공개 범위를 관리하세요.")
        SoftCard {
            OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("이름") }, singleLine = true)
            OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("닉네임") }, singleLine = true)
            ServerRegionPicker(api, region) { id, name -> regionId = id; region = name }
            ServerFacilityPicker(api, regionId, facilityName) { facilityName = it }
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("자기소개 (선택)") }, minLines = 2)
        }
        SoftCard {
            Text("프로필 사진", style = MaterialTheme.typography.titleMedium)
            PhotoActions(onSelected = { uri ->
                if (saving) return@PhotoActions
                saving = true; saveError = null
                scope.launch {
                    try {
                        val mediaId = withContext(Dispatchers.IO) { serverSocial.uploadImage(context, uri, "PROFILE") }
                        val saved = onSavePhoto(mediaId)
                        social.accounts.photo = saved.profileImageUrl
                    } catch (failure: Exception) { saveError = failure.message ?: "프로필 사진을 저장하지 못했습니다." }
                    finally { saving = false }
                }
            })
            if (profile.profileImageUrl != null) Text("새 사진을 선택하면 즉시 변경됩니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        }
        SoftCard {
            Text("관심 운동", style = MaterialTheme.typography.titleMedium)
            Sport.entries.chunked(3).forEach { sports ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sports.forEach { sport ->
                        FilterChip(interests.contains(sport), { interests = if (interests.contains(sport)) interests - sport else interests + sport }, { Text(sport.label) })
                    }
                }
            }
            OutlinedTextField(customInterestDraft, { customInterestDraft = it }, Modifier.fillMaxWidth(), label = { Text("직접 추가할 운동") }, singleLine = true)
            OutlinedButton({
                if (customInterestDraft.isNotBlank()) {
                    customInterests = customInterests + customInterestDraft.trim()
                    customInterestDraft = ""
                }
            }, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("관심 운동 추가", color = Mint) }
            if (customInterests.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    customInterests.forEach { interest -> FilterChip(true, { customInterests = customInterests - interest }, { Text("$interest ×") }) }
                }
            }
        }
        SoftCard {
            Text("운동 정보", style = MaterialTheme.typography.titleMedium)
            Text("운동 경력", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("입문", "6개월 미만", "1년 이상").forEach { option -> FilterChip(experience == option, { experience = option }, { Text(option) }) }
            }
            Text("운동 목표 · 복수 선택", style = MaterialTheme.typography.labelLarge)
            listOf("다이어트", "근육 증가", "체력 향상", "기록 향상", "운동 습관 만들기").chunked(3).forEach { goalRow ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goalRow.forEach { goal -> FilterChip(goals.contains(goal), { goals = if (goals.contains(goal)) goals - goal else goals + goal }, { Text(goal) }) }
                }
            }
            Text("주 운동 가능 시간", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("평일 아침", "평일 저녁", "주말").forEach { option -> FilterChip(availableTime == option, { availableTime = option }, { Text(option) }) }
            }
        }
        SoftCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("비공개 계정", style = MaterialTheme.typography.titleMedium); Text("프로필 상세는 승인된 팔로워에게 공개됩니다. 게시물의 공개 범위는 별도로 설정해요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary) }
                Switch(isPrivate, { isPrivate = it })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ isEditing = false }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("취소") }
            Button({ if (nickname.isNotBlank() && (interests.isNotEmpty() || customInterests.isNotEmpty()) && goals.isNotEmpty()) {
                val updated = profile.copy(nickname = nickname, region = region, activityRegionId = regionId, displayName = displayName, facilityName = facilityName, bio = bio, interests = interests, customInterests = customInterests, experience = experience, goals = goals, availableTime = availableTime, isPrivate = isPrivate)
                saving = true; saveError = null
                scope.launch { try { onSave(updated); isEditing = false } catch (failure: Exception) { saveError = failure.message } finally { saving = false } }
            } }, Modifier.weight(1f), enabled = !saving, shape = FitMateShapes.Button) { Text(if (saving) "저장 중…" else "저장") }
        }
        ErrorText(saveError)
        if (saveError != null) TextButton({ saving = true; scope.launch { try { onReload(); saveError = null; isEditing = false } catch (failure: Exception) { saveError = failure.message } finally { saving = false } } }, enabled = !saving) { Text("편집 취소하고 서버 최신 정보 불러오기") }
        if (saving) AlertDialog(onDismissRequest = {}, title = { Text("프로필 저장 중") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    } else {
        ProfileOverview(profile, social, workouts, runs, { isEditing = true }, onOpenInbody, onSamsungTest, onOpenAdminDemo, onAccount, onHistory, onNotifications, onReports, onOpenSettings, serverSocial, recordApi = api)
    }
}

@Composable private fun SafetyScreen(modifier: Modifier) = Screen(modifier) {
    SectionTitle("안전 · 신고", "운동 메이트 만남 전 안전 수칙을 확인하세요.")
    SoftCard { Text("정확한 집결 위치", fontWeight = FontWeight.Bold); Text("참가 확정자에게만 공개하고 공개 목록에는 대략 지역만 표시합니다.") }
    SoftCard { Text("신고", fontWeight = FontWeight.Bold); Text("프로필 · 게시물 · 댓글 · DM · 노쇼\n접수 → 검토 중 → 처리 완료/반려") }
    SoftCard { Text("차단", fontWeight = FontWeight.Bold); Text("서로 프로필·게시물·팔로우·DM·메이트 신청이 차단됩니다.") }
}

@Composable private fun AdminScreen(modifier: Modifier, onOpenAds: () -> Unit) = Screen(modifier) {
    SectionTitle("관리자 대시보드", "웹 운영 화면 목업 · 역할별 접근 제어")
    SoftCard { Text("오늘 현황", fontWeight = FontWeight.Bold); Text("신규 회원 128 · 운동 완료 67 · 대기 신고 4 · OCR 실패 2") }
    SoftCard { Text("운영 메뉴", fontWeight = FontWeight.Bold); Text("회원 · 게시물 · 댓글/DM 신고 · 카테고리 · 공지 · 광고 · 감사 로그") }
    SoftCard { Text("권한", fontWeight = FontWeight.Bold); Text("SUPER_ADMIN · ADMIN · MODERATOR · 관리자 2단계 인증") }
    OutlinedButton(onOpenAds, Modifier.fillMaxWidth()) { Text("제휴 광고 관리 보기") }
}

@Composable private fun AdsScreen(modifier: Modifier) = Screen(modifier) {
    SectionTitle("제휴 광고", "건강·민감 정보는 타겟팅에 사용하지 않습니다.")
    SoftCard { Text("성수 피트니스 1일 체험", fontWeight = FontWeight.Bold); Text("광고 · 성수동 · 헬스 · 오늘만 30% 할인") }
    SoftCard { Text("운영 기준", fontWeight = FontWeight.Bold); Text("홈 배너 · 메이트 목록 광고 카드 · 카테고리 하단 · 시설 추천\nDM·인바디·운동 기록 화면에는 노출하지 않음") }
    SoftCard { Text("관리 항목", fontWeight = FontWeight.Bold); Text("광고주 · 기간 · 노출 지역/종목 · 일일 빈도 · 노출/클릭/쿠폰 성과") }
}

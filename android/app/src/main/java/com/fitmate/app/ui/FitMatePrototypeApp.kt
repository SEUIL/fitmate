package com.fitmate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.MatePost
import com.fitmate.app.data.model.CommunityPost
import com.fitmate.app.data.model.DirectMessage
import com.fitmate.app.data.model.DmConversation
import com.fitmate.app.data.model.InbodyRecord
import com.fitmate.app.data.model.Sport
import com.fitmate.app.data.model.UserProfile
import com.fitmate.app.data.model.WorkoutSet
import com.fitmate.app.data.model.WorkoutSummary
import com.fitmate.app.data.repository.MockFitMateRepository
import com.fitmate.app.ui.theme.FitMateColors
import com.fitmate.app.ui.theme.FitMateShapes
import com.fitmate.app.ui.theme.FitMateSpacing
import com.fitmate.app.ui.theme.FitmateTheme

private enum class PrototypeRoute(val title: String) {
    ONBOARDING("운동 프로필"), HOME("FitMate"), SOLO("혼자 운동하기"), RUNNING("러닝"),
    MATE_LIST("같이 운동하기"), MATE_DETAIL("메이트 게시물"), MATE_WRITE("모집 게시물 작성"),
    WORKOUT("운동 진행"), REPORT("운동 리포트"), INBODY("인바디 등록"), COMMUNITY("커뮤니티"),
    DM("DM"), DM_DETAIL("대화"), PROFILE("프로필 · 설정"), SAFETY("안전 · 신고"), ADMIN("관리자"), ADS("제휴 광고")
}

private val Mint = FitMateColors.Primary
private val MintSoft = FitMateColors.PrimaryContainer
private val Lavender = FitMateColors.Background
private val Ink = FitMateColors.DarkSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitMatePrototypeApp(onOpenSamsungTest: () -> Unit) {
    val repository = remember { MockFitMateRepository }
    var route by remember { mutableStateOf(PrototypeRoute.ONBOARDING) }
    var profile by remember { mutableStateOf(repository.initialProfile()) }
    var matePosts by remember { mutableStateOf(repository.matePosts()) }
    var selectedMatePost by remember { mutableStateOf(matePosts.first()) }
    var latestWorkoutSummary by remember { mutableStateOf(WorkoutSummary(0, emptyList(), false)) }
    var communityPosts by remember { mutableStateOf(repository.communityPosts()) }
    var dmConversations by remember { mutableStateOf(repository.dmConversations()) }
    var selectedConversation by remember { mutableStateOf(dmConversations.first()) }
    var inbodyRecords by remember { mutableStateOf(listOf(InbodyRecord("2026.08.15", 68.2, 29.4, 18.7))) }
    Scaffold(
        topBar = {
            if (route != PrototypeRoute.HOME && route != PrototypeRoute.ONBOARDING) {
                TopAppBar(
                    title = { Text(if (route == PrototypeRoute.DM_DETAIL) selectedConversation.participantName else route.title) },
                    navigationIcon = {
                        Text("‹", modifier = Modifier.padding(horizontal = 20.dp).clickable {
                            route = when (route) {
                                PrototypeRoute.DM_DETAIL -> PrototypeRoute.DM
                                PrototypeRoute.MATE_DETAIL, PrototypeRoute.MATE_WRITE -> PrototypeRoute.MATE_LIST
                                PrototypeRoute.RUNNING -> PrototypeRoute.SOLO
                                else -> PrototypeRoute.HOME
                            }
                        }, style = MaterialTheme.typography.headlineMedium)
                    }
                )
            }
        },
        bottomBar = {
            if (route in setOf(PrototypeRoute.HOME, PrototypeRoute.MATE_LIST, PrototypeRoute.COMMUNITY, PrototypeRoute.DM, PrototypeRoute.PROFILE)) {
                BottomNavigation(route) { route = it }
            }
        }
    ) { padding ->
        when (route) {
            PrototypeRoute.ONBOARDING -> OnboardingScreen(Modifier.padding(padding)) { updatedProfile ->
                profile = updatedProfile
                route = PrototypeRoute.HOME
            }
            PrototypeRoute.HOME -> HomeScreen(Modifier.padding(padding), profile, matePosts, onRoute = { route = it })
            PrototypeRoute.SOLO -> SoloScreen(Modifier.padding(padding), { route = PrototypeRoute.WORKOUT }, { route = PrototypeRoute.RUNNING })
            PrototypeRoute.RUNNING -> RunningScreen(Modifier.padding(padding)) { route = PrototypeRoute.WORKOUT }
            PrototypeRoute.MATE_LIST -> MateListScreen(Modifier.padding(padding), matePosts, { post ->
                selectedMatePost = post
                route = PrototypeRoute.MATE_DETAIL
            }, { route = PrototypeRoute.MATE_WRITE })
            PrototypeRoute.MATE_DETAIL -> MateDetailScreen(Modifier.padding(padding), selectedMatePost) { route = PrototypeRoute.WORKOUT }
            PrototypeRoute.MATE_WRITE -> MateWriteScreen(Modifier.padding(padding)) { title, sport ->
                val newPost = MatePost(
                    id = (matePosts.maxOfOrNull { it.id } ?: 0) + 1,
                    title = title,
                    sport = sport,
                    region = profile.region,
                    dateTime = "날짜 선택 필요",
                    duration = "1시간",
                    capacity = 2,
                    participantCount = 1,
                    difficulty = "초보 환영",
                    description = "내 루틴에 맞는 운동 메이트를 모집합니다.",
                    authorName = profile.nickname,
                    isBeginnerWelcome = true
                )
                matePosts = listOf(newPost) + matePosts
                selectedMatePost = newPost
                route = PrototypeRoute.MATE_DETAIL
            }
            PrototypeRoute.WORKOUT -> WorkoutScreen(Modifier.padding(padding)) { summary ->
                latestWorkoutSummary = summary
                route = PrototypeRoute.REPORT
            }
            PrototypeRoute.REPORT -> ReportScreen(Modifier.padding(padding), latestWorkoutSummary) { route = PrototypeRoute.HOME }
            PrototypeRoute.INBODY -> InbodyScreen(Modifier.padding(padding), inbodyRecords) { record ->
                inbodyRecords = listOf(record) + inbodyRecords
            }
            PrototypeRoute.COMMUNITY -> CommunityScreen(Modifier.padding(padding), communityPosts, onWrite = { content ->
                communityPosts = listOf(CommunityPost((communityPosts.maxOfOrNull { it.id } ?: 0) + 1, profile.nickname, content, true, 0, 0, "방금 전")) + communityPosts
            }, onToggleLike = { postId ->
                communityPosts = communityPosts.map { post -> if (post.id == postId) post.copy(isLiked = !post.isLiked, likeCount = post.likeCount + if (post.isLiked) -1 else 1) else post }
            })
            PrototypeRoute.DM -> DmScreen(Modifier.padding(padding), dmConversations) { conversation ->
                selectedConversation = conversation
                route = PrototypeRoute.DM_DETAIL
            }
            PrototypeRoute.DM_DETAIL -> DmDetailScreen(Modifier.padding(padding), selectedConversation) { text ->
                val message = DirectMessage(System.currentTimeMillis(), text, true)
                selectedConversation = selectedConversation.copy(messages = selectedConversation.messages + message, unreadCount = 0)
                dmConversations = dmConversations.map { if (it.id == selectedConversation.id) selectedConversation else it }
            }
            PrototypeRoute.PROFILE -> ProfileScreen(
                modifier = Modifier.padding(padding),
                profile = profile,
                onSave = { profile = it },
                onOpenInbody = { route = PrototypeRoute.INBODY },
                onSamsungTest = onOpenSamsungTest,
                onOpenAdminDemo = { route = PrototypeRoute.ADMIN }
            )
            PrototypeRoute.SAFETY -> SafetyScreen(Modifier.padding(padding))
            PrototypeRoute.ADMIN -> AdminScreen(Modifier.padding(padding)) { route = PrototypeRoute.ADS }
            PrototypeRoute.ADS -> AdsScreen(Modifier.padding(padding))
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
        NavItem(PrototypeRoute.COMMUNITY, "커뮤니티", Icons.Outlined.Groups, selectedRoute, onRoute)
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
    val tint = if (selected) Mint else FitMateColors.NeutralIcon
    Column(
        modifier = Modifier.weight(1f).clickable { onRoute(route) }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable private fun Screen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = LazyColumn(
    modifier.fillMaxSize().background(Lavender)
) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FitMateSpacing.ScreenHorizontal, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(FitMateSpacing.Item),
            content = content
        )
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String? = null) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink); subtitle?.let { Text(it, color = Color.DarkGray) } }
@Composable private fun SoftCard(content: @Composable ColumnScope.() -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = FitMateColors.Surface), shape = FitMateShapes.Card, border = BorderStroke(1.dp, FitMateColors.Outline), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(FitMateSpacing.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
@Composable private fun PrimaryAction(label: String, onClick: () -> Unit) = Button(onClick, Modifier.fillMaxWidth().height(48.dp), shape = FitMateShapes.Button) { Text(label, style = MaterialTheme.typography.labelLarge) }

@Composable private fun OnboardingScreen(modifier: Modifier, done: (UserProfile) -> Unit) = Screen(modifier) {
    var step by remember { mutableStateOf(1) }
    var nickname by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("성수동") }
    var selectedSports by remember { mutableStateOf(setOf(Sport.FITNESS, Sport.RUNNING)) }
    Text("FITMATE", color = Mint, fontWeight = FontWeight.Bold)
    when (step) {
        1 -> {
            SectionTitle("반가워요!\n어떻게 불러드릴까요?", "이름 대신 사용할 닉네임이에요.")
            Text("1 / 3  기본 정보", color = Mint)
            OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("닉네임") }, singleLine = true)
            OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(), label = { Text("주 활동 지역") }, supportingText = { Text("정확한 주소가 아닌 동 단위로 표시돼요.") }, singleLine = true)
            Spacer(Modifier.height(180.dp)); PrimaryAction("다음") { if (nickname.isNotBlank()) step = 2 }
        }
        2 -> {
            SectionTitle("어떤 운동을\n좋아하시나요?", "관심 운동을 선택하면 맞는 메이트를 보여드려요.")
            Text("2 / 3  관심 운동 · 복수 선택", color = Mint)
            Sport.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { sport ->
                        FilterChip(selectedSports.contains(sport), { selectedSports = if (selectedSports.contains(sport)) selectedSports - sport else selectedSports + sport }, { Text(sport.label) })
                    }
                }
            }
            Spacer(Modifier.height(160.dp)); PrimaryAction("다음") { if (selectedSports.isNotEmpty()) step = 3 }
        }
        else -> {
            SectionTitle("프로필 설정 완료", "입력한 정보를 확인하고 FitMate를 시작하세요.")
            Text("3 / 3  내 운동 프로필", color = Mint)
            SoftCard {
                Text(nickname, fontWeight = FontWeight.Bold)
                Text("활동 지역 · $region")
                Text("관심 운동 · ${selectedSports.joinToString { it.label }}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("프로필은 언제든 수정할 수 있어요.", fontWeight = FontWeight.Bold)
                Text("메이트 모집 시에는 대략 지역만 표시되고, 정확한 위치는 참가 확정 후 공개됩니다.")
            }
            Spacer(Modifier.height(140.dp)); PrimaryAction("FitMate 시작하기") { done(UserProfile(nickname, region, selectedSports, true)) }
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
        Text("${profile.region} · ${profile.interests.joinToString { it.label }}", style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextSecondary)
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
    Box(Modifier.fillMaxWidth().height(6.dp).background(FitMateColors.Outline, CircleShape)) {
        Box(Modifier.fillMaxWidth(progress).height(6.dp).background(Mint, CircleShape))
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
    SectionTitle("혼자 운동하기", "오늘의 루틴을 선택해 시작하세요.")
    SoftCard { Text("추천 루틴", fontWeight = FontWeight.Bold); Text("전신 웨이트 · 약 60분"); PrimaryAction("추천 루틴 시작") { start() } }
    SoftCard { Text("저장한 루틴", fontWeight = FontWeight.Bold); Text("등 · 하체 루틴 · 이전 기록 불러오기"); OutlinedButton(start, Modifier.fillMaxWidth()) { Text("이 루틴으로 시작") } }
    SoftCard { Text("러닝", fontWeight = FontWeight.Bold); Text("거리·시간·페이스 목표와 GPS 기록"); OutlinedButton(running, Modifier.fillMaxWidth()) { Text("러닝 설정") } }
    SoftCard { Text("직접 구성", fontWeight = FontWeight.Bold); Text("운동 추가·삭제·순서 변경 · kg/lb 설정") }
}

@Composable private fun RunningScreen(modifier: Modifier, start: () -> Unit) = Screen(modifier) {
    SectionTitle("러닝 설정", "운동 목표와 기록 방식을 선택하세요.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("자유", "거리", "시간", "페이스", "인터벌").forEachIndexed { index, text -> FilterChip(index == 1, {}, { Text(text) }) } }
    SoftCard { Text("목표 거리", fontWeight = FontWeight.Bold); Text("5 km · 예상 35분 · 목표 페이스 7'00\"/km") }
    SoftCard { Text("기록 설정", fontWeight = FontWeight.Bold); Text("GPS 경로 기록 · 자동 일시정지 · 출발/도착 비식별 처리") }
    SoftCard { Text("안전 안내", fontWeight = FontWeight.Bold); Text("워밍업, 날씨·미세먼지 확인 후 시작하세요.") }
    PrimaryAction("러닝 시작") { start() }
}

@Composable private fun MateListScreen(modifier: Modifier, posts: List<MatePost>, open: (MatePost) -> Unit, write: () -> Unit) = Screen(modifier) {
    var selectedSport by remember { mutableStateOf<Sport?>(null) }
    val filteredPosts = posts.filter { selectedSport == null || it.sport == selectedSport }
    SectionTitle("같이 운동하기", "내 주변 메이트를 찾아보세요.")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selectedSport == null, { selectedSport = null }, { Text("전체") }); Sport.entries.take(3).forEach { sport -> FilterChip(selectedSport == sport, { selectedSport = sport }, { Text(sport.label) }) } }
    Text("${filteredPosts.size}개의 모집 글", color = Color.DarkGray)
    filteredPosts.forEach { post -> SoftCard { Text(post.sport.label, color = Mint, fontWeight = FontWeight.Bold); Text(post.title, fontWeight = FontWeight.Bold); Text("${post.region} · ${post.dateTime} · ${post.duration}"); Text("${post.difficulty} · ${post.participantCount}/${post.capacity}명${if (post.isBeginnerWelcome) " · 초보 환영" else ""}", color = Color.DarkGray); Text("상세 보기  →", color = Mint, modifier = Modifier.clickable { open(post) }) } }
    PrimaryAction("+ 메이트 모집 글 작성") { write() }
}

@Composable private fun MateDetailScreen(modifier: Modifier, post: MatePost, start: () -> Unit) = Screen(modifier) {
    SectionTitle(post.title, "${post.region} · ${post.dateTime} · ${post.duration} · 모집 ${post.participantCount}/${post.capacity}명")
    SoftCard { Text("운동 정보", fontWeight = FontWeight.Bold); Text("${post.sport.label} · ${post.difficulty}${if (post.isBeginnerWelcome) " · 초보자 환영" else ""}"); Text(post.description) }
    SoftCard { Text("참가 확정 후", fontWeight = FontWeight.Bold); Text("정확한 집결 위치 공개 · DM 시작 · 일정 변경 알림\n각자 Samsung Health Data 사용 여부를 선택합니다.") }
    PrimaryAction("참가 신청 · 데모에서 확정") { start() }
    OutlinedButton({}, Modifier.fillMaxWidth()) { Text("신고 · 차단") }
}

@Composable private fun MateWriteScreen(modifier: Modifier, done: (String, Sport) -> Unit) = Screen(modifier) {
    var title by remember { mutableStateOf("") }
    var selectedSport by remember { mutableStateOf(Sport.FITNESS) }
    SectionTitle("메이트 모집 글 작성", "카테고리에 맞는 조건을 입력하세요.")
    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("모집 제목") }, supportingText = { Text("예: 오늘 저녁 성수동에서 하체 운동 같이 해요") })
    SoftCard { Text("운동 종목", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Sport.entries.take(3).forEach { sport -> FilterChip(selectedSport == sport, { selectedSport = sport }, { Text(sport.label) }) } }; Text("헬스는 운동 형태 · 부위 · 진행 방식 · 시간 · 헬스장 조건을 추가 입력합니다.") }
    SoftCard { Text("공통 입력", fontWeight = FontWeight.Bold); Text("제목 · 날짜/시간 · 대략 지역 · 모집 인원 · 난이도 · 비용 · 공개 범위") }
    SoftCard { Text("안전", fontWeight = FontWeight.Bold); Text("정확한 집결 위치는 참가 확정자에게만 공개됩니다.") }
    PrimaryAction("모집 게시") { if (title.isNotBlank()) done(title, selectedSport) }
}

@Composable private fun WorkoutScreen(modifier: Modifier, finish: (WorkoutSummary) -> Unit) = Screen(modifier) {
    var useSamsung by remember { mutableStateOf(false) }
    var exerciseName by remember { mutableStateOf("랫풀다운") }
    var weightText by remember { mutableStateOf("30") }
    var repetitionText by remember { mutableStateOf("12") }
    var restSeconds by remember { mutableStateOf(90) }
    val recordedSets = remember { mutableStateListOf<WorkoutSet>() }
    val currentVolume = recordedSets.sumOf { it.weightKg * it.repetitions }

    SectionTitle("운동 진행", "공통 운동하기 모듈 · 혼자/메이트 공용")
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Samsung Health Data", fontWeight = FontWeight.Bold); Text("이번 운동에만 선택적으로 사용") }
            Switch(useSamsung, { useSamsung = it })
        }
        Text(if (useSamsung) "기기에서 먼저 운동 시작 → FitMate 기록 → 기기 종료 후 상세 로그 동기화" else "FitMate 직접 기록으로 기본 리포트를 생성합니다.")
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("운동 중", color = Color(0xFF6FF1D0), fontWeight = FontWeight.Bold)
            Text("00:00:00", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text("기록 ${recordedSets.size}세트 · 총 볼륨 ${"%.0f".format(currentVolume)} kg", color = Color(0xFFD3E4DF))
        }
    }
    SoftCard {
        Text("세트 기록", fontWeight = FontWeight.Bold)
        OutlinedTextField(exerciseName, { exerciseName = it }, Modifier.fillMaxWidth(), label = { Text("운동명") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weightText, { weightText = it }, Modifier.weight(1f), label = { Text("중량 (kg)") }, singleLine = true)
            OutlinedTextField(repetitionText, { repetitionText = it }, Modifier.weight(1f), label = { Text("반복 횟수") }, singleLine = true)
        }
        PrimaryAction("이 세트 기록") {
            val weight = weightText.toDoubleOrNull()
            val repetitions = repetitionText.toIntOrNull()
            if (exerciseName.isNotBlank() && weight != null && repetitions != null && weight >= 0 && repetitions > 0) {
                recordedSets += WorkoutSet(exerciseName, weight, repetitions)
            }
        }
    }
    SoftCard {
        Text("휴식 타이머", fontWeight = FontWeight.Bold)
        Text("${restSeconds / 60}:${(restSeconds % 60).toString().padStart(2, '0')}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ restSeconds = 60 }, Modifier.weight(1f)) { Text("1분") }
            OutlinedButton({ restSeconds = 90 }, Modifier.weight(1f)) { Text("1분 30초") }
            OutlinedButton({ restSeconds = 120 }, Modifier.weight(1f)) { Text("2분") }
        }
    }
    if (recordedSets.isNotEmpty()) {
        SoftCard {
            Text("기록한 세트", fontWeight = FontWeight.Bold)
            recordedSets.forEachIndexed { index, set -> Text("${index + 1}세트 · ${set.exerciseName} · ${set.weightKg} kg × ${set.repetitions}회") }
        }
    }
    PrimaryAction("운동 종료하고 리포트 보기") { finish(WorkoutSummary(0, recordedSets.toList(), useSamsung)) }
}

@Composable private fun ReportScreen(modifier: Modifier, summary: WorkoutSummary, home: () -> Unit) = Screen(modifier) {
    val isEmpty = summary.sets.isEmpty()
    SectionTitle("운동 리포트", "오늘 · 헬스 · 개인 기록")
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("오늘도 완료했어요! ✓", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${summary.sets.size}세트", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(if (isEmpty) "다음 운동부터 세트를 기록해 보세요." else "총 볼륨 ${"%.0f".format(summary.totalVolumeKg)} kg · 운동 ${summary.sets.map { it.exerciseName }.distinct().size}종", color = Color.White)
        }
    }
    SoftCard {
        Text("기본 리포트", fontWeight = FontWeight.Bold)
        if (isEmpty) Text("기록된 수동 세트가 없습니다.") else summary.sets.forEach { set -> Text("${set.exerciseName} · ${set.weightKg} kg × ${set.repetitions}회") }
    }
    if (summary.usedSamsungHealth) {
        SoftCard { Text("Samsung Health 상세 리포트", fontWeight = FontWeight.Bold); Text("운동 종료 후 로그 동기화를 기다리고 있어요. 수신되면 칼로리·심박수·거리 등 제공 가능한 값이 추가됩니다.") }
    } else {
        SoftCard { Text("Samsung Health 미사용", fontWeight = FontWeight.Bold); Text("이번 리포트는 FitMate에 직접 기록한 운동 정보만으로 구성됐어요.") }
    }
    SoftCard { Text("다음 목표", fontWeight = FontWeight.Bold); Text("이번 주 운동 3회 중 2회 달성") }
    PrimaryAction("홈으로") { home() }
}

@Composable private fun InbodyScreen(modifier: Modifier, records: List<InbodyRecord>, onSave: (InbodyRecord) -> Unit) = Screen(modifier) {
    var isReviewing by remember { mutableStateOf(false) }
    var measuredAt by remember { mutableStateOf("2026.09.03") }
    var weight by remember { mutableStateOf("68.2") }
    var skeletalMuscle by remember { mutableStateOf("29.4") }
    var bodyFat by remember { mutableStateOf("18.7") }
    SectionTitle("인바디 등록", "판독한 수치는 저장 전 직접 확인할 수 있어요.")
    if (!isReviewing) {
        SoftCard {
            Text("인바디 결과표", style = MaterialTheme.typography.titleMedium)
            Text("사진을 선택하면 체중·골격근량·체지방률을 판독해 검수 화면에 표시합니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            PrimaryAction("결과표 사진 선택") { isReviewing = true }
        }
        SoftCard {
            Text("직접 입력도 가능해요", style = MaterialTheme.typography.titleMedium)
            Text("기기나 결과표가 없을 때도 측정값을 직접 기록할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            OutlinedButton({ isReviewing = true }, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("직접 입력", color = Mint) }
        }
    } else {
        SoftCard {
            Text("판독 결과 검수", style = MaterialTheme.typography.titleMedium)
            Text("낮은 신뢰도의 값은 수정한 뒤 저장하세요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            OutlinedTextField(measuredAt, { measuredAt = it }, Modifier.fillMaxWidth(), label = { Text("측정일 (YYYY.MM.DD)") }, singleLine = true)
            OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text("체중 (kg)") }, singleLine = true)
            OutlinedTextField(skeletalMuscle, { skeletalMuscle = it }, Modifier.fillMaxWidth(), label = { Text("골격근량 (kg)") }, singleLine = true)
            OutlinedTextField(bodyFat, { bodyFat = it }, Modifier.fillMaxWidth(), label = { Text("체지방률 (%)") }, singleLine = true)
        }
        PrimaryAction("검수 후 저장") {
            val parsedWeight = weight.toDoubleOrNull()
            val parsedMuscle = skeletalMuscle.toDoubleOrNull()
            val parsedFat = bodyFat.toDoubleOrNull()
            if (parsedWeight != null && parsedMuscle != null && parsedFat != null) {
                onSave(InbodyRecord(measuredAt, parsedWeight, parsedMuscle, parsedFat))
                isReviewing = false
            }
        }
    }
    if (records.isNotEmpty()) {
        Text("변화 기록", style = MaterialTheme.typography.titleLarge)
        records.take(3).forEachIndexed { index, record ->
            SoftCard {
                Text(record.measuredAt, style = MaterialTheme.typography.labelMedium, color = FitMateColors.TextSecondary)
                Text("체중 ${record.weightKg} kg · 골격근량 ${record.skeletalMuscleKg} kg · 체지방률 ${record.bodyFatPercent}%", style = MaterialTheme.typography.bodyMedium)
                if (index == 0 && records.size > 1) {
                    val previous = records[1]
                    Text("이전 대비 체중 ${"%+.1f".format(record.weightKg - previous.weightKg)} kg", style = MaterialTheme.typography.bodySmall, color = Mint)
                }
            }
        }
        Text("인바디 수치는 의료 진단이 아닌 개인 운동 기록으로만 제공합니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    }
}

@Composable private fun CommunityScreen(
    modifier: Modifier,
    posts: List<CommunityPost>,
    onWrite: (String) -> Unit,
    onToggleLike: (Long) -> Unit
) = Screen(modifier) {
    var followingOnly by remember { mutableStateOf(true) }
    var isComposerOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val visiblePosts = posts.filter { !followingOnly || it.isFollowing }

    SectionTitle("커뮤니티", "운동 기록과 일상을 공유해 보세요.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(followingOnly, { followingOnly = true }, { Text("팔로잉") })
        FilterChip(!followingOnly, { followingOnly = false }, { Text("전체 공개") })
    }
    if (isComposerOpen) {
        SoftCard {
            Text("새 게시물", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("오늘의 운동이나 생각을 적어보세요") }, minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ isComposerOpen = false }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("취소") }
                Button({ if (draft.isNotBlank()) { onWrite(draft); draft = ""; isComposerOpen = false } }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("게시") }
            }
        }
    } else {
        OutlinedButton({ isComposerOpen = true }, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("+ 게시물 작성", color = Mint) }
    }
    visiblePosts.forEach { post -> CommunityPostCard(post) { onToggleLike(post.id) } }
    Text("비공개 게시물은 맞팔한 사용자에게만 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
}

@Composable private fun CommunityPostCard(post: CommunityPost, onToggleLike: () -> Unit) = SoftCard {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(post.authorName.take(1), MintSoft, Mint, size = 30.dp)
        Column { Text(post.authorName, style = MaterialTheme.typography.labelLarge); Text(post.createdAt, style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary) }
    }
    Text(post.content, style = MaterialTheme.typography.bodyMedium)
    HorizontalDivider(color = FitMateColors.Outline)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (post.isLiked) "♥ ${post.likeCount}" else "♡ ${post.likeCount}", color = if (post.isLiked) Mint else FitMateColors.TextSecondary, modifier = Modifier.clickable { onToggleLike() })
        Text("댓글 ${post.commentCount}", color = FitMateColors.TextSecondary)
    }
}

@Composable private fun DmScreen(modifier: Modifier, conversations: List<DmConversation>, onOpen: (DmConversation) -> Unit) = Screen(modifier) {
    SectionTitle("DM", "운동 메이트와 일정을 편하게 조율하세요.")
    SoftCard {
        Text("메시지 요청 1", style = MaterialTheme.typography.titleMedium)
        Text("비공개 계정에는 맞팔 후 대화를 시작할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({}, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("거절") }; Button({}, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("수락") } }
    }
    conversations.forEach { conversation ->
        Card(Modifier.fillMaxWidth().clickable { onOpen(conversation) }, colors = CardDefaults.cardColors(containerColor = FitMateColors.Surface), shape = FitMateShapes.Card, border = BorderStroke(1.dp, FitMateColors.Outline), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Row(Modifier.padding(FitMateSpacing.CompactPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(conversation.participantName.take(1), MintSoft, Mint)
                Column(Modifier.weight(1f)) { Text(conversation.participantName, style = MaterialTheme.typography.titleMedium); Text(conversation.lastMessage, style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary, maxLines = 1) }
                if (conversation.unreadCount > 0) Box(Modifier.size(20.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) { Text(conversation.unreadCount.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White) }
            }
        }
    }
    Text("차단 관계에서는 DM과 메시지 요청이 모두 차단됩니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
}

@Composable private fun DmDetailScreen(modifier: Modifier, conversation: DmConversation, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(Color(0xFFEAF1F5)).imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ChatDateDivider("오늘") }
            items(conversation.messages, key = { it.id }) { message -> ChatBubble(message) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(FitMateColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지 입력") },
                singleLine = true,
                shape = FitMateShapes.Button
            )
            Button(
                onClick = { if (draft.isNotBlank()) { onSend(draft); draft = "" } },
                shape = FitMateShapes.Button,
                modifier = Modifier.height(44.dp)
            ) { Text("전송", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable private fun ChatDateDivider(label: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center
) { Text(label, style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary) }

@Composable private fun ChatBubble(message: DirectMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (message.isMine) Text("오후 8:32", style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary, modifier = Modifier.padding(end = 5.dp))
        Card(
            modifier = Modifier.fillMaxWidth(0.76f),
            colors = CardDefaults.cardColors(containerColor = if (message.isMine) MintSoft else FitMateColors.Surface),
            shape = if (message.isMine) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            border = if (message.isMine) null else BorderStroke(1.dp, FitMateColors.Outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) { Text(message.text, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyMedium, color = FitMateColors.TextPrimary) }
        if (!message.isMine) Text("오후 8:31", style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable private fun ProfileScreen(
    modifier: Modifier,
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onOpenInbody: () -> Unit,
    onSamsungTest: () -> Unit,
    onOpenAdminDemo: () -> Unit
) = Screen(modifier) {
    var isEditing by remember { mutableStateOf(false) }
    var nickname by remember(profile) { mutableStateOf(profile.nickname) }
    var region by remember(profile) { mutableStateOf(profile.region) }
    var interests by remember(profile) { mutableStateOf(profile.interests) }
    var isPrivate by remember(profile) { mutableStateOf(profile.isPrivate) }

    if (isEditing) {
        SectionTitle("프로필 수정", "내 운동 정보와 공개 범위를 관리하세요.")
        SoftCard {
            OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("닉네임") }, singleLine = true)
            OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(), label = { Text("주 활동 지역") }, singleLine = true)
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
        }
        SoftCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("비공개 계정", style = MaterialTheme.typography.titleMedium); Text("맞팔한 사용자만 내 게시물을 볼 수 있어요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary) }
                Switch(isPrivate, { isPrivate = it })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ isEditing = false }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("취소") }
            Button({ if (nickname.isNotBlank() && interests.isNotEmpty()) { onSave(profile.copy(nickname = nickname, region = region, interests = interests, isPrivate = isPrivate)); isEditing = false } }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("저장") }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(profile.nickname.take(1), MintSoft, Mint, size = 48.dp)
            Column {
                Text(profile.nickname, style = MaterialTheme.typography.titleLarge)
                Text("${if (profile.isPrivate) "비공개" else "공개"} 계정 · ${profile.region}", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            }
        }
        SoftCard {
            Text("내 운동 프로필", style = MaterialTheme.typography.titleMedium)
            Text("관심 운동 · ${profile.interests.joinToString { it.label }}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton({ isEditing = true }, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("프로필 수정", color = Mint) }
        }
        SoftCard {
            Text("건강 기록", style = MaterialTheme.typography.titleMedium)
            Text("인바디 기록을 등록하고 변화를 확인하세요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            OutlinedButton(onOpenInbody, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("인바디 기록 보기", color = Mint) }
        }
        SoftCard {
            Text("연결 및 알림", style = MaterialTheme.typography.titleMedium)
            Text("Samsung Health Data 연결/해제 · 권한 상태 · Push 알림 설정", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            OutlinedButton(onSamsungTest, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("Samsung Health 연결 관리", color = Mint) }
        }
        SoftCard { Text("관계 관리", style = MaterialTheme.typography.titleMedium); Text("팔로워 삭제 · 차단 목록 · 알림 설정", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary) }
        OutlinedButton(onOpenAdminDemo, Modifier.fillMaxWidth(), shape = FitMateShapes.Button) { Text("운영자 전용 데모 열기") }
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

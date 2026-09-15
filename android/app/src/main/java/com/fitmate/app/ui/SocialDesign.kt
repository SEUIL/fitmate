package com.fitmate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.ServerSocialApi
import com.fitmate.app.ui.theme.FitMateColors
import com.fitmate.app.ui.theme.FitMateShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val FeedBackground: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

@Composable internal fun ProfileIdentityRow(name: String, photo: String?, counts: List<String>, server: ServerSocialApi? = null, accent: Boolean = false, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (photo == null) { if (accent) SocialAccentAvatar(name, 80) else SocialAvatar(name, 80) }
        else Box(Modifier.size(80.dp).clip(CircleShape)) { FitMatePhoto(photo, server, square = true) }
        listOf("운동 기록", "팔로워", "팔로잉").forEachIndexed { index, label ->
            Column(Modifier.weight(1f).clickable { select(label) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(counts[index], fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun ProfilePostGrid(entries: List<FeedEntry>, server: ServerSocialApi? = null, open: (FeedEntry) -> Unit) {
    Column(Modifier.animateContentSize(tween(MotionDuration)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    entries.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        row.forEach { entry -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { open(entry) }) {
            if (entry.photo != null) FitMatePhoto(entry.photo, server, square = true)
            else Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (entry.workout != null) Icons.Outlined.FitnessCenter else Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(18.dp))
                Text(entry.workout ?: entry.post.content, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        } }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    } }
    }
}

@Composable internal fun SocialAvatar(name: String, size: Int = 40) {
    Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1), fontWeight = FontWeight.SemiBold, fontSize = (size * .36).sp, color = FitMateColors.TextPrimary)
    }
}

@Composable internal fun SocialTitle(title: String, action: ImageVector, description: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        IconButton(onAction) { Icon(action, description, tint = FitMateColors.TextPrimary) }
    }
}

@Composable internal fun SocialTabs(options: List<String>, selected: String, accent: Boolean = false, onSelect: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            options.forEach { label ->
                val selectedColor = if (accent) MaterialTheme.colorScheme.primary else FitMateColors.TextPrimary
                val indicator by animateColorAsState(if (selected == label) selectedColor else Color.Transparent, tween(MotionDuration), label = "tabIndicator")
                Column(Modifier.weight(1f).selectable(selected = selected == label, role = Role.Tab, onClick = { onSelect(label) }), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, Modifier.padding(vertical = 16.dp), fontWeight = if (selected == label) FontWeight.Bold else FontWeight.Normal, color = if (selected == label) selectedColor else FitMateColors.TextSecondary)
                    Box(Modifier.fillMaxWidth().height(2.dp).background(indicator))
                }
            }
        }
        HorizontalDivider(color = FitMateColors.Outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SocialSheet(dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun EditorialPost(entry: FeedEntry, onLike: () -> Unit, onComments: () -> Unit, menu: Map<String, () -> Unit> = emptyMap(), onAuthor: (() -> Unit)? = null, server: ServerSocialApi? = null, redesigned: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val heartScale = likeScale(entry.post.isLiked)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (redesigned) 18.dp else 10.dp),
        border = if (redesigned) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (redesigned) SocialAccentAvatar(entry.post.authorName) else SocialAvatar(entry.post.authorName, 32)
            Column(Modifier.weight(1f).padding(start = 10.dp).then(if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.post.authorName, modifier = Modifier.weight(1f, fill = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatPostTime(entry.post.createdAt), style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary)
                }
                Text(entry.visibility, style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary)
            }
            if (menu.isNotEmpty()) Box {
                IconButton({ expanded = true }) { Icon(Icons.Outlined.MoreHoriz, "게시물 더 보기") }
                FitMatePostMenu(expanded, { expanded = false }, menu, redesigned)
            }
        }
        Text(entry.post.content, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp, color = FitMateColors.TextPrimary)
        PostPhotos(entry, server)
        entry.workout?.let { summary ->
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.FitnessCenter, "운동 기록", tint = FitMateColors.Primary)
                Text("TODAY’S WORKOUT", style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary)
                Text(summary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 18.dp), thickness = 0.5.dp, color = FitMateColors.Outline)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onLike, colors = ButtonDefaults.textButtonColors(contentColor = if (entry.post.isLiked) Color(0xFFD73549) else FitMateColors.TextPrimary)) {
            Icon(if (entry.post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (entry.post.isLiked) "좋아요 취소" else "좋아요", Modifier.size(20.dp).graphicsLayer { scaleX = heartScale; scaleY = heartScale })
            Spacer(Modifier.width(6.dp))
            Text(entry.post.likeCount.toString(), fontSize = 13.sp, fontWeight = FontWeight.Normal)
        }
        TextButton(onComments, colors = ButtonDefaults.textButtonColors(contentColor = FitMateColors.TextPrimary)) {
            Icon(Icons.Outlined.ChatBubbleOutline, "댓글 보기", Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(entry.comments.size.toString(), fontSize = 13.sp, fontWeight = FontWeight.Normal)
        }
    }
    }
}

@Composable private fun FitMatePostMenu(expanded: Boolean, dismiss: () -> Unit, actions: Map<String, () -> Unit>, redesigned: Boolean = false) {
    FitMateMenuSurface(expanded, dismiss, Modifier.width(188.dp)) {
        actions.entries.forEachIndexed { index, (label, action) ->
            val destructive = label in setOf("게시물 신고", "차단", "삭제")
            DropdownMenuItem(
                text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                onClick = { dismiss(); action() },
                modifier = if (redesigned) Modifier.heightIn(min = 48.dp) else Modifier.height(38.dp),
                colors = MenuDefaults.itemColors(textColor = if (destructive) MaterialTheme.colorScheme.error else FitMateColors.TextPrimary),
                contentPadding = PaddingValues(horizontal = 18.dp)
            )
            if (index < actions.size - 1) HorizontalDivider(color = FitMateColors.Outline, thickness = 0.5.dp)
        }
    }
}

@Composable internal fun FitMateMenuSurface(
    expanded: Boolean,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = dismiss,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, FitMateColors.Outline),
        content = content
    )
}

@Composable internal fun FitMateWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Fit", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, color = MaterialTheme.colorScheme.onSurface)
        Text("Mate", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable internal fun SocialHome(modifier: Modifier, profile: UserProfile, social: SocialState, workoutCount: Int, runCount: Int, start: () -> Unit, mates: () -> Unit, community: () -> Unit, dm: () -> Unit, inbody: () -> Unit, resuming: Boolean = false, notifications: () -> Unit = {}, server: ServerSocialApi? = null, history: () -> Unit = {}, resumeDescription: String? = null) = Screen(modifier, background = MaterialTheme.colorScheme.background) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FitMateWordmark(Modifier.weight(1f))
        IconButton(notifications) {
            BadgedBox(badge = { if (social.accounts.notices.any { !it.read }) Badge { Text(social.accounts.notices.count { !it.read }.toString()) } }) {
                Icon(Icons.Outlined.NotificationsNone, "알림")
            }
        }
        IconButton(dm) { Icon(Icons.Outlined.ChatBubbleOutline, "DM 열기") }
    }
    Text("오늘도,\n나답게 운동해요", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 12.dp))
    WorkoutCard {
        Text(if (resuming) "진행 중인 운동" else "나의 운동", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(if (resuming) "운동을 이어가 볼까요?" else "오늘의 운동을 시작해요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (resuming) resumeDescription ?: "진행 중인 운동으로 돌아가요" else "헬스부터 러닝까지, 나의 페이스로", color = FitMateColors.TextSecondary)
        WorkoutAction(if (resuming) "운동 이어하기" else "운동 시작", onClick = start)
    }
    WorkoutCard {
        Text("기기에 저장된 운동", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Triple("헬스", workoutCount, Icons.Outlined.FitnessCenter), Triple("러닝", runCount, Icons.AutoMirrored.Outlined.DirectionsRun)).forEach { (label, count, icon) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, null, Modifier.size(28.dp))
                    Column {
                        Text(label, color = FitMateColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("${count}회", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("메이트", Icons.Outlined.Groups, mates), Triple("운동 기록", Icons.AutoMirrored.Outlined.Notes, history), Triple("인바디", Icons.Outlined.AccessibilityNew, inbody)).forEach { (label, icon, action) ->
            val interaction = remember { MutableInteractionSource() }
            Card(
                modifier = Modifier.weight(1f).heightIn(min = 84.dp).pressFeedback(interaction).clip(RoundedCornerShape(16.dp))
                    .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = action),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    Text(label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("커뮤니티", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(community) { Text("더보기") }
        IconButton({ social.openComposer = true; community() }) { Icon(Icons.Outlined.AddBox, "게시물 작성") }
    }
    val feed = social.feed.filter { it.post.authorName !in social.blocked && (it.mine || it.visibility == "전체 공개" || it.post.authorName in social.mutual) }
    if (feed.isEmpty()) {
        Text("아직 올라온 운동 이야기가 없어요.", color = FitMateColors.TextSecondary)
        TextButton({ social.openComposer = true; community() }) { Text("첫 이야기 남기기") }
    }
    feed.take(5).forEach { entry -> key(entry.post.id) {
        EditorialPost(entry, {
            val index = social.feed.indexOfFirst { it.post.id == entry.post.id }
            if (index >= 0) social.feed[index] = entry.copy(post = entry.post.copy(isLiked = !entry.post.isLiked, likeCount = entry.post.likeCount + if (entry.post.isLiked) -1 else 1))
        }, { social.selectedPostId = entry.post.id; community() }, onAuthor = { if (!entry.mine) social.accounts.opened = entry.post.authorName }, server = server)
    } }
    TextButton(community, Modifier.fillMaxWidth()) { Text("커뮤니티에서 더 보기", color = FitMateColors.TextPrimary) }
}

internal fun profileDisplayCounts(serverBacked: Boolean, serverCounts: List<String>?, localCounts: List<String>): List<String> =
    if (serverBacked) serverCounts ?: listOf("—", "—", "—") else localCounts

@Composable internal fun ProfileOverview(profile: UserProfile, social: SocialState, workouts: List<WorkoutSummary>, runs: List<RunResult>, edit: () -> Unit, inbody: () -> Unit, samsung: () -> Unit, admin: () -> Unit, account: () -> Unit, history: () -> Unit, notifications: () -> Unit, reports: () -> Unit, openSettings: () -> Unit, server: ServerSocialApi? = null, recordApi: FitMateApiClient? = null, selfUserId: String = "") {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("게시물") }
    var opened by remember { mutableStateOf<FeedEntry?>(null) }
    val mine = social.feed.filter { it.mine }
    val fallbackCounts = listOf(
        (workouts.size + runs.size).toString(),
        social.accounts.followers.size.toString(),
        social.following.size.toString(),
    )
    var serverCounts by remember(server, recordApi, selfUserId) { mutableStateOf<List<String>?>(null) }
    var countsFailed by remember(server, recordApi, selfUserId) { mutableStateOf(false) }
    var countsAttempt by remember(server, recordApi, selfUserId) { mutableStateOf(0) }
    LaunchedEffect(server, recordApi, selfUserId, countsAttempt) {
        if (server == null || recordApi == null || selfUserId.isBlank()) return@LaunchedEffect
        countsFailed = false
        runCatching {
            withContext(Dispatchers.IO) {
                val serverProfile = server.profile(selfUserId)
                val records = ServerRecordApi(recordApi)
                listOf(
                    (records.completedCount("workout-sessions?status=COMPLETED") + records.completedCount("running-records")).toString(),
                    (serverProfile.get("followerCount")?.asLong ?: 0L).toString(),
                    (serverProfile.get("followingCount")?.asLong ?: 0L).toString(),
                )
            }
        }.onSuccess { serverCounts = it }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            countsFailed = true
        }
    }
    SocialScreenTitle(profile.nickname, action = { IconButton(openSettings) { Icon(Icons.Outlined.Menu, "프로필 설정") } })
    ProfileIdentityRow(profile.nickname, profile.profileImageUrl ?: social.accounts.photo,
        profileDisplayCounts(server != null || recordApi != null, serverCounts, fallbackCounts), server, accent = true) {
        if (it == "운동 기록") tab = it else social.accounts.directory = it
    }
    if (countsFailed) TextButton({ countsAttempt++ }) { Text("프로필 통계를 불러오지 못했어요 · 다시 시도") }
    Text(profile.displayName.ifBlank { profile.nickname }, fontWeight = FontWeight.SemiBold)
    if (profile.bio.isNotBlank()) Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
    Text("${profile.region} · ${(profile.interests.map { it.label } + profile.customInterests).joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(edit, Modifier.weight(1f).heightIn(min = 56.dp), shape = FitMateShapes.Button) { Text("프로필 편집", color = FitMateColors.TextPrimary) }
        OutlinedButton(inbody, Modifier.weight(1f).heightIn(min = 56.dp), shape = FitMateShapes.Button) { Text("인바디 기록", color = FitMateColors.TextPrimary) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (tab == "게시물") 3.dp else 12.dp)) {
    SocialTabs(listOf("게시물", "운동 기록"), tab, accent = true) { tab = it }
    if (tab == "게시물") {
        if (mine.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.GridView, null, Modifier.size(36.dp))
            Text("나의 첫 운동 이야기", fontWeight = FontWeight.Bold)
            Text("커뮤니티에 작성한 글이 여기에 모여요.", color = FitMateColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        ProfilePostGrid(mine, server) { opened = it }
    } else {
        if (recordApi != null) ServerRecordList(recordApi)
        else WorkoutRecordBrowser(workouts, runs)
    }
    }
    opened?.let { entry -> SocialSheet({ opened = null }) {
        Text(entry.post.authorName, fontWeight = FontWeight.Bold)
        PostPhotos(entry, server)
        entry.workout?.let { Text(it) }
        Text(entry.post.content)
        Text("${entry.visibility} · ${entry.comments.size}개의 댓글", color = FitMateColors.TextSecondary)
        entry.comments.forEach { Text("${it.author}: ${it.text}") }
    } }
}

@Composable internal fun SettingsScreen(
    modifier: Modifier,
    profile: UserProfile,
    onProfileEdit: () -> Unit,
    onSamsungHealth: () -> Unit,
    onAccount: () -> Unit,
    onLogout: () -> Unit,
    onAppearance: () -> Unit,
    onNotifications: () -> Unit,
    onFollowRequests: () -> Unit,
    onReports: () -> Unit,
    onOpenAdmin: () -> Unit,
    remembersAccount: Boolean = false,
) = Screen(modifier, background = MaterialTheme.colorScheme.background) {
    var confirmLogout by remember { mutableStateOf(false) }
    SettingsGroup("계정 및 프로필") {
        SettingsRow(Icons.Outlined.PersonOutline, "프로필 편집", "이름, 활동 지역, 소개를 관리해요", onProfileEdit)
        SettingsRow(Icons.Outlined.Lock, "공개 범위", if (profile.isPrivate) "현재 비공개 계정" else "현재 공개 계정", onProfileEdit)
        SettingsRow(Icons.Outlined.Image, "프로필 사진", "프로필 편집에서 사진을 변경해요", onProfileEdit)
        SettingsRow(Icons.Outlined.Lock, "계정·개인정보", "비밀번호, 동의와 기기 데이터를 관리해요", onAccount)
        SettingsRow(Icons.Outlined.Logout, "로그아웃", if (remembersAccount) "로그인 화면에서 기억한 계정으로 다시 접속할 수 있어요" else "이 기기에서 안전하게 로그아웃합니다", { confirmLogout = true })
    }
    SettingsGroup("운동 및 기기") {
        SettingsRow(Icons.Outlined.FitnessCenter, "운동 프로필", "관심 운동, 목표, 가능 시간을 설정해요", onProfileEdit)
        SettingsRow(Icons.Outlined.FavoriteBorder, "Samsung Health", "연결 상태와 운동 기록 동기화를 관리해요", onSamsungHealth, trailing = "연결 관리")
    }
    SettingsGroup("앱 환경") {
        SettingsRow(Icons.Outlined.LightMode, "화면 모드", "시스템 설정, 라이트, 다크", onAppearance, trailing = "변경")
        SettingsRow(Icons.Outlined.NotificationsNone, "알림 설정", "메이트, DM, 커뮤니티 알림을 관리해요", onNotifications)
    }
    SettingsGroup("지원") {
        SettingsRow(Icons.Outlined.GroupAdd, "팔로우 요청", "승인 대기 중인 요청을 확인해요", onFollowRequests)
        SettingsRow(Icons.Outlined.GppMaybe, "내 신고 내역", "신고 처리 상태를 확인해요", onReports)
    }
    DebugOnly { SettingsGroup("개발") { SettingsRow(Icons.Outlined.AdminPanelSettings, "운영자 센터", "운영자 권한이 있는 계정만 이용할 수 있습니다", onOpenAdmin) } }
    if (confirmLogout) StateConfirm(
        "로그아웃",
        if (remembersAccount) "로그인 화면으로 이동합니다. 이 기기에 기억한 계정은 비밀번호 없이 다시 접속할 수 있습니다. 간편 접속을 해제하려면 로그인 화면에서 ‘기억한 계정 삭제’를 눌러 주세요."
        else "현재 기기의 로그인 세션을 종료하고 로그인 화면으로 이동합니다. 기기 내 운동 기록은 유지됩니다.",
        { confirmLogout = false },
        confirm = { confirmLogout = false; onLogout() }
    )
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    WorkoutCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable private fun SettingsRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        }
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f) trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = FitMateColors.TextSecondary)
    }
    HorizontalDivider(color = FitMateColors.Outline)
}

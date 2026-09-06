package com.fitmate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitmate.app.data.model.*
import com.fitmate.app.R
import com.fitmate.app.ui.theme.FitMateColors
import com.fitmate.app.ui.theme.FitMateShapes

internal val FeedBackground: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

@Composable internal fun ProfileIdentityRow(name: String, photo: String?, counts: List<String>, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (photo == null) SocialAvatar(name, 80)
        else Box(Modifier.size(80.dp).clip(CircleShape)) { LocalPhoto(photo, square = true) }
        listOf("운동 기록", "팔로워", "팔로잉").forEachIndexed { index, label ->
            Column(Modifier.weight(1f).clickable { select(label) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(counts[index], fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun ProfilePostGrid(entries: List<FeedEntry>, open: (FeedEntry) -> Unit) {
    Column(Modifier.animateContentSize(tween(MotionDuration)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    entries.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        row.forEach { entry -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { open(entry) }) {
            if (entry.photo != null) LocalPhoto(entry.photo, square = true)
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

@Composable internal fun SocialTabs(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            options.forEach { label ->
                val indicator by animateColorAsState(if (selected == label) FitMateColors.TextPrimary else Color.Transparent, tween(MotionDuration), label = "tabIndicator")
                Column(Modifier.weight(1f).selectable(selected = selected == label, role = Role.Tab, onClick = { onSelect(label) }), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, Modifier.padding(vertical = 16.dp), fontWeight = if (selected == label) FontWeight.Bold else FontWeight.Normal, color = if (selected == label) FitMateColors.TextPrimary else FitMateColors.TextSecondary)
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

@Composable internal fun EditorialPost(entry: FeedEntry, onLike: () -> Unit, onComments: () -> Unit, menu: Map<String, () -> Unit> = emptyMap(), onAuthor: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    val heartScale = likeScale(entry.post.isLiked)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(entry.post.authorName, 32)
            Column(Modifier.weight(1f).padding(start = 10.dp).then(if (onAuthor != null) Modifier.clickable(onClick = onAuthor) else Modifier)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.post.authorName, modifier = Modifier.weight(1f, fill = false), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.post.createdAt, style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary)
                }
                Text(entry.visibility, style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary)
            }
            if (menu.isNotEmpty()) Box {
                IconButton({ expanded = true }) { Icon(Icons.Outlined.MoreHoriz, "게시물 더 보기") }
                FitMatePostMenu(expanded, { expanded = false }, menu)
            }
        }
        Text(entry.post.content, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp, color = FitMateColors.TextPrimary)
        entry.photo?.let { LocalPhoto(it, square = true) }
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

@Composable private fun FitMatePostMenu(expanded: Boolean, dismiss: () -> Unit, actions: Map<String, () -> Unit>) {
    FitMateMenuSurface(expanded, dismiss, Modifier.width(188.dp)) {
        actions.entries.forEachIndexed { index, (label, action) ->
            val destructive = label in setOf("게시물 신고", "차단", "삭제")
            DropdownMenuItem(
                text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                onClick = { dismiss(); action() },
                modifier = Modifier.height(38.dp),
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

@Composable internal fun SocialHome(modifier: Modifier, profile: UserProfile, social: SocialState, workoutCount: Int, runCount: Int, start: () -> Unit, mates: () -> Unit, community: () -> Unit, dm: () -> Unit, inbody: () -> Unit, resuming: Boolean = false, notifications: () -> Unit = {}) = Screen(modifier, background = FeedBackground) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(40.dp).background(Color(0xFF1F2927), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.fitmate_symbol), contentDescription = "FitMate 로고", modifier = Modifier.size(32.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fit", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Mate", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(notifications) {
            BadgedBox(badge = { if (social.accounts.notices.any { !it.read }) Badge { Text(social.accounts.notices.count { !it.read }.toString()) } }) {
                Icon(Icons.Outlined.NotificationsNone, "알림")
            }
        }
        IconButton(dm) { Icon(Icons.Outlined.ChatBubbleOutline, "DM 열기") }
    }
    HorizontalDivider(color = FitMateColors.Outline)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("메이트", Icons.Outlined.Groups, mates), Triple("운동 일지", Icons.Outlined.GridView, community), Triple("인바디", Icons.Outlined.Insights, inbody)).forEach { (label, icon, action) ->
            val interaction = remember { MutableInteractionSource() }
            Card(
                modifier = Modifier.weight(1f).height(126.dp).pressFeedback(interaction).clip(RoundedCornerShape(18.dp))
                    .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = action),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, FitMateColors.Outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(vertical = 18.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                ) {
                    Icon(icon, null, tint = FitMateColors.TextPrimary, modifier = Modifier.size(38.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${profile.nickname}님의 오늘", style = MaterialTheme.typography.titleMedium)
            Text("운동 기록 · 헬스 $workoutCount · 러닝 $runCount", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        }
        val startInteraction = remember { MutableInteractionSource() }
        Button(start, modifier = Modifier.pressFeedback(startInteraction), interactionSource = startInteraction, shape = FitMateShapes.Button, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Text(if (resuming) "운동 이어하기" else "운동 시작") }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("운동하는 일상", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        }, { social.selectedPostId = entry.post.id; community() }, onAuthor = { if (!entry.mine) social.accounts.opened = entry.post.authorName })
    } }
    TextButton(community, Modifier.fillMaxWidth()) { Text("커뮤니티에서 더 보기", color = FitMateColors.TextPrimary) }
}

@Composable internal fun ProfileOverview(profile: UserProfile, social: SocialState, workouts: List<WorkoutSummary>, runs: List<RunResult>, edit: () -> Unit, inbody: () -> Unit, samsung: () -> Unit, admin: () -> Unit, account: () -> Unit, history: () -> Unit, notifications: () -> Unit, reports: () -> Unit) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("게시물") }
    var settings by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<FeedEntry?>(null) }
    val mine = social.feed.filter { it.mine }
    SocialTitle(profile.nickname, Icons.Outlined.Menu, "프로필 설정") { settings = true }
    ProfileIdentityRow(profile.nickname, social.accounts.photo, listOf((workouts.size + runs.size).toString(), social.accounts.followers.size.toString(), social.following.size.toString())) {
        if (it == "운동 기록") tab = it else social.accounts.directory = it
    }
    Text(profile.displayName.ifBlank { profile.nickname }, fontWeight = FontWeight.SemiBold)
    if (profile.bio.isNotBlank()) Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
    Text("${profile.region} · ${(profile.interests.map { it.label } + profile.customInterests).joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(edit, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("프로필 편집", color = FitMateColors.TextPrimary) }
        OutlinedButton(inbody, Modifier.weight(1f), shape = FitMateShapes.Button) { Text("인바디 기록", color = FitMateColors.TextPrimary) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (tab == "게시물") 3.dp else 12.dp)) {
    SocialTabs(listOf("게시물", "운동 기록"), tab) { tab = it }
    if (tab == "게시물") {
        if (mine.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.GridView, null, Modifier.size(36.dp))
            Text("나의 첫 운동 이야기", fontWeight = FontWeight.Bold)
            Text("커뮤니티에 작성한 글이 여기에 모여요.", color = FitMateColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        ProfilePostGrid(mine) { opened = it }
    } else {
        WorkoutRecordBrowser(workouts, runs)
    }
    }
    opened?.let { entry -> SocialSheet({ opened = null }) {
        Text(entry.post.authorName, fontWeight = FontWeight.Bold)
        entry.photo?.let { LocalPhoto(it, square = true) }
        entry.workout?.let { Text(it) }
        Text(entry.post.content)
        Text("${entry.visibility} · ${entry.comments.size}개의 댓글", color = FitMateColors.TextSecondary)
        entry.comments.forEach { Text("${it.author}: ${it.text}") }
    } }
    if (settings) SocialSheet({ settings = false }) {
        Text("설정 및 운동 정보", style = MaterialTheme.typography.titleLarge)
        Text("${if (profile.isPrivate) "비공개" else "공개"} 계정 · ${profile.experience}")
        Text("${profile.goals.joinToString()} · ${profile.availableTime}")
        if (profile.facilityName.isNotBlank()) Text(profile.facilityName)
        TextButton({ settings = false; edit() }) { Text("운동 프로필 · 공개 범위 수정") }
        TextButton({ settings = false; samsung() }) { Text("Samsung Health 연결 관리") }
        TextButton({ settings = false; account() }) { Text("계정·개인정보 관리") }
        com.fitmate.app.ui.theme.AppearanceSettings()
        TextButton({ settings = false; notifications() }) { Text("알림·알림 설정") }
        TextButton({ settings = false; reports() }) { Text("내 신고 내역") }
        TextButton({ settings = false; social.accounts.directory = "팔로우 요청" }) { Text("팔로우 요청 관리") }
        TextButton({ settings = false; social.accounts.directory = "사용자" }) { Text("사용자 둘러보기") }
        Text("프로필 사진", style = MaterialTheme.typography.titleMedium)
        PhotoActions({ social.accounts.photo = it })
        if (social.accounts.photo != null) TextButton({ social.accounts.photo = null }) { Text("프로필 사진 제거") }
        DebugOnly {
            var tools by remember { mutableStateOf(false) }
            TextButton({ tools = !tools }) { Text("개발 및 테스트 도구") }
            if (tools) TextButton({ settings = false; admin() }) { Text("운영자 데모") }
        }
    }
}

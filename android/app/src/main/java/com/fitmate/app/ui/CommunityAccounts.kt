package com.fitmate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.fitmate.app.ui.theme.FitMateColors
import com.fitmate.app.ui.theme.FitMateShapes
import com.fitmate.app.data.remote.ServerSocialApi
import com.fitmate.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MemberPreview(
    val name: String,
    val private: Boolean = false,
    val bio: String = "함께 꾸준히 운동해요.",
)
internal data class AppNotice(val id: Long, val text: String, val category: String, val destination: String, val target: String? = null, val read: Boolean = false)
internal data class ReportTicket(val id: Long, val target: String, val reason: String, val detail: String, val status: String = "RECEIVED", val result: String = "서버 전송 전 로컬 접수")
internal data class ReportTarget(val label: String, val targetType: String? = null, val targetId: String? = null)
private data class DirectoryUser(val id: String, val nickname: String, val status: String? = null)

private fun previewDirectoryUsers(mode: String): List<DirectoryUser> = when (mode) {
    "팔로워" -> listOf(DirectoryUser("", "민준"), DirectoryUser("", "수연"))
    "팔로잉" -> listOf(DirectoryUser("", "민준"), DirectoryUser("", "지현"))
    "팔로우 요청" -> listOf(DirectoryUser("", "요청 메이트", "PENDING"))
    else -> emptyList()
}
internal class CommunityAccounts {
    val people = listOf(
        MemberPreview("민준", bio = "성수동 · 헬스 · 주 3회 운동 중"),
        MemberPreview("수연", bio = "러닝 · 주말 한강 러닝 좋아해요"),
        MemberPreview("지현", bio = "클라이밍 · 초급부터 같이해요"),
        MemberPreview("비공개 메이트", true),
        MemberPreview("요청 메이트", true),
    )
    var opened by mutableStateOf<String?>(null)
    var directory by mutableStateOf<String?>(null)
    var directoryUserId by mutableStateOf<String?>(null)
    var followers by mutableStateOf(setOf("민준", "수연"))
    var incoming by mutableStateOf(mapOf("요청 메이트" to "PENDING"))
    var outgoing by mutableStateOf(emptyMap<String, String>())
    var photo by mutableStateOf<String?>(null)
    var reportTarget by mutableStateOf<ReportTarget?>(null)
    val reports = mutableStateListOf<ReportTicket>()
    val notices = mutableStateListOf<AppNotice>()
    var push by mutableStateOf(false)
    var preferences by mutableStateOf(mapOf("DM" to true, "메이트" to true, "커뮤니티" to true, "운동" to true, "안내" to true))
    fun reconcile(state: SocialState) { state.mutual = state.following.intersect(followers) - state.blocked }
    fun follow(state: SocialState, name: String) {
        if (name in state.blocked) return
        if (name in state.following) { state.following = state.following - name; outgoing = outgoing + (name to "CANCELED") }
        else if (outgoing[name] == "PENDING") outgoing = outgoing + (name to "CANCELED")
        else if (people.any { it.name == name && it.private }) outgoing = outgoing + (name to "PENDING")
        else { state.following = state.following + name; outgoing = outgoing + (name to "ACCEPTED") }
        reconcile(state)
    }
    fun resolveOutgoing(state: SocialState, name: String, accepted: Boolean) {
        if (name in state.blocked || outgoing[name] != "PENDING") return
        outgoing = outgoing + (name to if (accepted) "ACCEPTED" else "REJECTED")
        if (accepted) state.following = state.following + name
        reconcile(state)
        notify(state, "팔로우 요청 ${if (accepted) "수락" else "거절"} 예시", "커뮤니티", "PERSON", name)
    }
    fun respond(state: SocialState, name: String, accept: Boolean) {
        if (name in state.blocked || incoming[name] != "PENDING") return
        incoming = incoming + (name to if (accept) "ACCEPTED" else "REJECTED")
        if (accept) followers = followers + name
        reconcile(state)
    }
    fun visibleProfile(state: SocialState, name: String) = name !in state.blocked && (people.none { it.name == name && it.private } || name in state.following)
    fun canDm(state: SocialState, name: String) = name !in state.blocked && (people.none { it.name == name && it.private } || name in state.following)
    fun notify(state: SocialState, text: String, category: String, destination: String, target: String? = null) {
        notices.add(0, AppNotice(state.nextId(), text, category, destination, target))
    }
}

@Composable internal fun CommunityAccountSheets(state: SocialState, dm: (ChatRoom) -> Unit, server: ServerSocialApi? = null, myUserId: String = "") {
    val accounts = state.accounts
    accounts.directory?.let { mode -> SocialSheet({ accounts.directory = null }) {
        val scope = rememberCoroutineScope()
        val targetUserId = accounts.directoryUserId?.takeIf { mode != "팔로우 요청" } ?: myUserId
        var serverUsers by remember(mode, targetUserId) { mutableStateOf<List<DirectoryUser>?>(null) }
        var serverError by remember(mode, targetUserId) { mutableStateOf<String?>(null) }
        LaunchedEffect(mode, targetUserId, server) {
            if (server == null || targetUserId.isBlank()) return@LaunchedEffect
            runCatching { withContext(Dispatchers.IO) {
                when (mode) {
                    "팔로워" -> server.followers(targetUserId)
                    "팔로잉" -> server.following(targetUserId)
                    else -> server.followRequests()
                }
            } }.onSuccess { rows ->
                state.rememberServerUsers(rows)
                serverUsers = rows.map { DirectoryUser(it.text("userId"), it.text("nickname"), if (mode == "팔로우 요청") "PENDING" else it.getAsJsonObject("relationship")?.text("followingStatus")) }
            }.onFailure { serverError = it.message ?: "목록을 불러오지 못했습니다." }
        }
        Text(mode, style = MaterialTheme.typography.titleLarge)
        Text(
            when (mode) {
                "팔로워" -> "나를 팔로우하는 사람"
                "팔로잉" -> "내가 팔로우하는 사람"
                else -> "승인 대기 중인 팔로우 요청"
            },
            style = MaterialTheme.typography.bodySmall,
            color = FitMateColors.TextSecondary,
        )
        Choices(listOf("팔로워", "팔로잉", "팔로우 요청"), mode) { accounts.directory = it }
        ErrorText(serverError)
        val users = serverUsers ?: when (mode) {
            "팔로워" -> accounts.followers.map { DirectoryUser(state.serverUserId(it).orEmpty(), it) }
            "팔로잉" -> state.following.map { DirectoryUser(state.serverUserId(it).orEmpty(), it) }
            "팔로우 요청" -> accounts.incoming.map { DirectoryUser(state.serverUserId(it.key).orEmpty(), it.key, it.value) }
            else -> emptyList()
        }.filterNot { it.nickname in state.blocked }
        val preview = users.isEmpty() && BuildConfig.DEBUG
        val visibleUsers = if (preview) previewDirectoryUsers(mode) else users
        if (preview) Text("개발용 미리보기입니다. 실제 출시 화면에서는 서버 관계 데이터만 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
        if (visibleUsers.isEmpty()) Text("아직 표시할 사람이 없어요.")
        visibleUsers.forEach { user -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val member = accounts.people.firstOrNull { it.name == user.nickname }
            SocialAvatar(user.nickname, 44)
            Column(Modifier.weight(1f).clickable { accounts.opened = user.nickname; accounts.directory = null }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.nickname, fontWeight = FontWeight.SemiBold)
                    if (member?.private == true) Icon(Icons.Outlined.Lock, "비공개 계정", Modifier.size(14.dp), tint = FitMateColors.TextSecondary)
                }
                Text(member?.bio ?: "FitMate 사용자", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary, maxLines = 1)
            }
            if (mode == "팔로우 요청") {
                if (user.status == "PENDING") {
                    TextButton({
                        if (server != null && user.id.isNotBlank()) scope.launch { runCatching { withContext(Dispatchers.IO) { server.acceptFollowRequest(user.id) } }.onSuccess { serverUsers = serverUsers?.filterNot { it.id == user.id } } }
                        else accounts.respond(state, user.nickname, true)
                    }) { Text("수락") }
                    TextButton({
                        if (server != null && user.id.isNotBlank()) scope.launch { runCatching { withContext(Dispatchers.IO) { server.rejectFollowRequest(user.id) } }.onSuccess { serverUsers = serverUsers?.filterNot { it.id == user.id } } }
                        else accounts.respond(state, user.nickname, false)
                    }) { Text("거절") }
                } else Text(user.status.orEmpty())
            } else if (mode == "팔로워") TextButton({
                if (server != null && user.id.isNotBlank()) scope.launch { runCatching { withContext(Dispatchers.IO) { server.removeFollower(user.id) } }.onSuccess { serverUsers = serverUsers?.filterNot { it.id == user.id } } }
                else { accounts.followers = accounts.followers - user.nickname; accounts.reconcile(state) }
            }) { Text("삭제") }
            else OutlinedButton({
                if (server != null && user.id.isNotBlank()) scope.launch {
                    runCatching { withContext(Dispatchers.IO) { server.unfollow(user.id) } }
                        .onSuccess { serverUsers = serverUsers?.filterNot { it.id == user.id } }
                } else {
                    state.following = state.following - user.nickname
                    accounts.reconcile(state)
                }
            }, shape = FitMateShapes.Button) { Text("팔로잉") }
        } }
        if (mode == "팔로우 요청") DebugOnly {
            TextButton({ accounts.incoming = accounts.incoming + ("요청 메이트" to "PENDING") }) { Text("테스트: 요청 1건 받기") }
        }
    } }
    accounts.reportTarget?.let { target -> SocialSheet({ accounts.reportTarget = null }) {
        val scope = rememberCoroutineScope()
        var reason by remember(target) { mutableStateOf("부적절한 콘텐츠") }
        var detail by remember(target) { mutableStateOf("") }
        var error by remember(target) { mutableStateOf<String?>(null) }
        Text("신고", style = MaterialTheme.typography.titleLarge)
        Text(target.label)
        Choices(listOf("부적절한 콘텐츠", "욕설 또는 괴롭힘", "광고 또는 스팸", "사칭", "노쇼 또는 반복적인 일정 취소", "기타"), reason) { reason = it }
        OutlinedTextField(detail, { detail = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("상세 사유 (선택)") })
        if (target.targetType != null && target.targetId != null && server != null) {
            Text("신고 내용은 서버로 전송되며, 운영 검토 후 처리됩니다.", style = MaterialTheme.typography.bodySmall)
        } else Text("이 대상은 서버 식별자가 없어 앱 실행 중에만 접수 상태를 보관합니다.", style = MaterialTheme.typography.bodySmall)
        ErrorText(error)
        PrimaryAction(if (target.targetType != null && target.targetId != null && server != null) "신고 접수" else "로컬 신고 접수") {
            val type = target.targetType
            val id = target.targetId
            if (server != null && type != null && id != null) scope.launch {
                val serverReason = when (reason) {
                    "광고 또는 스팸" -> "SPAM"
                    "욕설 또는 괴롭힘" -> "HARASSMENT"
                    else -> "OTHER"
                }
                runCatching { withContext(Dispatchers.IO) { server.report(type, id, serverReason, detail.ifBlank { null }) } }
                    .onSuccess { response ->
                        accounts.reports.add(0, ReportTicket(state.nextId(), target.label, reason, detail, response.text("status"), "서버에 신고가 접수되었습니다."))
                        accounts.notify(state, "신고가 접수되었어요.", "안내", "REPORTS")
                        accounts.reportTarget = null
                    }.onFailure { error = it.message ?: "신고를 접수하지 못했습니다." }
            } else {
                accounts.reports.add(0, ReportTicket(state.nextId(), target.label, reason, detail))
                accounts.notify(state, "신고를 로컬 접수했어요.", "안내", "REPORTS")
                accounts.reportTarget = null
            }
        }
    } }
}

@Composable internal fun FollowRequestsScreen(modifier: Modifier, state: SocialState, server: ServerSocialApi? = null) = Screen(modifier, background = MaterialTheme.colorScheme.background) {
    val accounts = state.accounts
    val scope = rememberCoroutineScope()
    var serverUsers by remember(server) { mutableStateOf<List<DirectoryUser>?>(null) }
    var error by remember(server) { mutableStateOf<String?>(null) }
    LaunchedEffect(server) {
        if (server == null) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { server.followRequests() } }
            .onSuccess { rows ->
                state.rememberServerUsers(rows)
                serverUsers = rows.map { DirectoryUser(it.text("userId"), it.text("nickname"), "PENDING") }
            }
            .onFailure { error = it.message ?: "팔로우 요청을 불러오지 못했습니다." }
    }
    Text("나를 팔로우하려는 사람을 승인하거나 거절할 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    ErrorText(error)
    val users = (serverUsers ?: accounts.incoming.map { DirectoryUser(state.serverUserId(it.key).orEmpty(), it.key, it.value) })
        .filter { it.status == "PENDING" && it.nickname !in state.blocked }
    val preview = users.isEmpty() && BuildConfig.DEBUG
    val visibleUsers = if (preview) previewDirectoryUsers("팔로우 요청") else users
    if (preview) Text("개발용 미리보기입니다. 실제 출시 화면에서는 서버 요청만 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    if (visibleUsers.isEmpty()) Text("대기 중인 팔로우 요청이 없어요.", style = MaterialTheme.typography.bodyMedium)
    visibleUsers.forEach { user ->
        val person = accounts.people.firstOrNull { it.name == user.nickname }
        WorkoutCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialAccentAvatar(user.nickname, 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.nickname, fontWeight = FontWeight.SemiBold)
                Text(person?.bio ?: "FitMate 사용자", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialPrimaryButton("수락", Modifier.weight(1f), onClick = {
                if (server != null && user.id.isNotBlank()) scope.launch {
                    runCatching { withContext(Dispatchers.IO) { server.acceptFollowRequest(user.id) } }
                        .onSuccess { serverUsers = serverUsers?.filterNot { it.id == user.id } }
                } else accounts.respond(state, user.nickname, true)
            })
            TextButton({
                if (server != null && user.id.isNotBlank()) scope.launch {
                    runCatching { withContext(Dispatchers.IO) { server.rejectFollowRequest(user.id) } }
                        .onSuccess { serverUsers = serverUsers?.filterNot { it.id == user.id } }
                } else accounts.respond(state, user.nickname, false)
            }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("거절", color = FitMateColors.TextSecondary) }
        }
        }
    }
}

@Composable internal fun MemberProfileScreen(modifier: Modifier, state: SocialState, back: () -> Unit, dm: (ChatRoom) -> Unit, server: ServerSocialApi? = null) = Screen(modifier) {
    val accounts = state.accounts
    accounts.opened?.let { name ->
        val scope = rememberCoroutineScope()
        val serverUserId = state.serverUserId(name)
        LaunchedEffect(server, serverUserId) {
            if (server != null && serverUserId != null) {
                runCatching { withContext(Dispatchers.IO) { server.profile(serverUserId) } }
                    .onSuccess(state::rememberServerProfile)
            }
        }
        val person = accounts.people.firstOrNull { it.name == name } ?: MemberPreview(name)
        val serverProfile = state.serverProfiles[name]
        val privateAccount = serverProfile?.isPrivate ?: person.private
        val canView = serverProfile?.canViewProfile ?: accounts.visibleProfile(state, name)
        val following = serverProfile?.followingStatus == "ACCEPTED" || name in state.following
        var tab by remember(name) { mutableStateOf("게시물") }
        var options by remember(name) { mutableStateOf(false) }
        var information by remember(name) { mutableStateOf<String?>(null) }
        var selected by remember(name) { mutableStateOf<FeedEntry?>(null) }
        var firstMessage by remember(name) { mutableStateOf(false) }
        var draftMessage by remember(name) { mutableStateOf("") }
        var dmError by remember(name) { mutableStateOf<String?>(null) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
            Box(Modifier.weight(1f)) { SocialTitle(name, Icons.Outlined.MoreHoriz, "사용자 옵션") { options = true } }
        }
        ProfileIdentityRow(name, serverProfile?.profileImageUrl, listOf("—", serverProfile?.followerCount?.toString() ?: "—", serverProfile?.followingCount?.toString() ?: "—"), server) {
            if (it == "운동 기록") tab = it
            else if (serverUserId != null) { accounts.directory = it; accounts.directoryUserId = serverUserId }
            else information = "상대방의 $it 정보는 서버 연결 후 제공됩니다."
        }
        Text(serverProfile?.displayName ?: name, fontWeight = FontWeight.SemiBold)
        if (name in state.blocked) {
            Text("차단한 사용자입니다. 프로필과 게시물이 숨겨져 있어요.")
            TextButton({
                if (server != null && serverUserId != null) scope.launch {
                    runCatching { withContext(Dispatchers.IO) { server.unblock(serverUserId) } }
                        .onSuccess { state.blocked = state.blocked - name }
                } else state.blocked = state.blocked - name
            }) { Text("차단 해제") }
        } else {
            if (canView) Text(serverProfile?.bio ?: person.bio, style = MaterialTheme.typography.bodyMedium)
            Text(if (privateAccount) "비공개 계정" else "공개 계정", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    if (server != null && serverUserId != null) scope.launch {
                        runCatching { withContext(Dispatchers.IO) { if (following) server.unfollow(serverUserId) else server.follow(serverUserId); server.profile(serverUserId) } }
                            .onSuccess { profile ->
                                state.rememberServerProfile(profile)
                                state.following = if (profile.getAsJsonObject("relationship").text("followingStatus") == "ACCEPTED") state.following + name else state.following - name
                            }
                    } else accounts.follow(state, name)
                }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text(when { following -> "팔로잉"; serverProfile?.followingStatus == "PENDING" || accounts.outgoing[name] == "PENDING" -> "요청 취소"; else -> "팔로우" }, color = FitMateColors.TextPrimary) }
                OutlinedButton({
                    if (server != null && serverUserId != null) firstMessage = true
                    else {
                        val room = state.chat(name)
                        room.status = if (privateAccount && name !in state.mutual) "요청 대기" else "대화 가능"
                        dm(room)
                    }
                }, Modifier.weight(1f), shape = FitMateShapes.Button, enabled = accounts.canDm(state, name)) { Text(if (privateAccount && name !in state.mutual) "DM 요청" else "메시지") }
            }
            if (!canView) Text("팔로우가 승인되면 프로필 상세를 볼 수 있어요.")
            val entries = state.feed.filter { it.post.authorName == name && (it.visibility == "전체 공개" || name in state.mutual) }
            Column(verticalArrangement = Arrangement.spacedBy(if (tab == "게시물") 3.dp else 12.dp)) {
            SocialTabs(listOf("게시물", "운동 기록"), tab) { tab = it }
            if (tab == "게시물") {
                if (entries.isEmpty()) Text("표시할 게시물이 없습니다.", Modifier.padding(vertical = 32.dp), color = FitMateColors.TextSecondary)
                ProfilePostGrid(entries, server) { selected = it }
            } else {
                Text("공개된 운동 기록", style = MaterialTheme.typography.titleSmall)
                if (entries.none { it.workout != null }) Text("공개 게시물에 첨부된 운동 기록이 없어요.", Modifier.padding(vertical = 24.dp), color = FitMateColors.TextSecondary)
                entries.filter { it.workout != null }.forEach { entry -> OutlinedCard(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(entry.workout.orEmpty()); Text(formatPostTime(entry.post.createdAt), style = MaterialTheme.typography.bodySmall) }
                } }
            }
        }
        }
        selected?.let { entry -> SocialSheet({ selected = null }) {
            Text(entry.post.authorName, fontWeight = FontWeight.SemiBold)
            entry.photo?.let { LocalPhoto(it, square = true) }
            entry.workout?.let { Text(it) }
            Text(entry.post.content)
            Text("${entry.visibility} · ${entry.comments.size}개의 댓글", color = FitMateColors.TextSecondary)
            entry.comments.forEach { Text("${it.author}: ${it.text}") }
        } }
        information?.let { text -> SocialSheet({ information = null }) { Text(text) } }
        if (firstMessage) SocialSheet({ firstMessage = false }) {
            Text(if (privateAccount && name !in state.mutual) "DM 요청" else "새 메시지", style = MaterialTheme.typography.titleLarge)
            Text("첫 메시지를 보내면 상대방에게 대화 요청이 전달됩니다.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(draftMessage, { draftMessage = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("메시지") }, minLines = 3)
            ErrorText(dmError)
            Button(onClick = {
                if (server != null && serverUserId != null) scope.launch {
                    runCatching { withContext(Dispatchers.IO) { server.startConversation(serverUserId, draftMessage.trim()); server.conversations() } }
                        .onSuccess { conversations ->
                            state.replaceServerConversations(conversations)
                            state.serverChatForUser(serverUserId)?.let(dm)
                            firstMessage = false
                        }.onFailure { dmError = it.message ?: "메시지 요청을 보내지 못했습니다." }
                }
            }, enabled = draftMessage.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("보내기") }
        }
        if (options) SocialSheet({ options = false }) {
            Text("사용자 옵션", style = MaterialTheme.typography.titleLarge)
            TextButton({ options = false; accounts.reportTarget = ReportTarget("프로필: $name", "USER", serverUserId) }) { Text("프로필 신고") }
            TextButton({
                if (server != null && serverUserId != null) scope.launch {
                    runCatching { withContext(Dispatchers.IO) { server.block(serverUserId) } }.onSuccess {
                        state.blocked = state.blocked + name; state.following = state.following - name; accounts.followers = accounts.followers - name; accounts.reconcile(state)
                    }
                } else { state.blocked = state.blocked + name; state.following = state.following - name; accounts.followers = accounts.followers - name; accounts.reconcile(state) }
                options = false
            }) { Text("차단") }
            if (accounts.outgoing[name] == "PENDING" && name !in state.blocked) DebugOnly {
                Text("개발용: 상대방 응답 예시", style = MaterialTheme.typography.labelSmall)
                Row {
                    TextButton({ accounts.resolveOutgoing(state, name, true) }) { Text("수락 받기") }
                    TextButton({ accounts.resolveOutgoing(state, name, false) }) { Text("거절 받기") }
                }
            }
        }
    }
}

@Composable internal fun NotificationFlow(modifier: Modifier, state: SocialState, open: (AppNotice) -> Unit, server: ServerSocialApi? = null) = Screen(modifier, background = MaterialTheme.colorScheme.background) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(server) {
        if (server != null) runCatching { withContext(Dispatchers.IO) { server.notifications() } }.onSuccess(state::replaceServerNotices)
    }
    val accounts = state.accounts
    var settings by remember { mutableStateOf(false) }
    state.notice?.let { Text(it) }
    var clear by remember { mutableStateOf(false) }
    var management by remember { mutableStateOf(false) }
    val unread = accounts.notices.count { !it.read }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (unread > 0) "읽지 않은 알림 $unread" else "모든 알림을 확인했어요", Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge, color = FitMateColors.TextSecondary)
        IconButton({ settings = true }) { Icon(Icons.Outlined.Settings, "알림 설정", Modifier.size(20.dp)) }
        Box {
            IconButton({ management = true }) { Icon(Icons.Outlined.MoreHoriz, "알림 관리", Modifier.size(22.dp)) }
            DropdownMenu(management, { management = false }) {
                DropdownMenuItem(text = { Text("모두 읽음") }, enabled = unread > 0, onClick = {
                    accounts.notices.indices.forEach { i -> accounts.notices[i] = accounts.notices[i].copy(read = true) }
                    if (server != null) scope.launch { runCatching { withContext(Dispatchers.IO) { server.readAllNotifications() } } }
                    management = false
                })
                DropdownMenuItem(text = { Text("전체 삭제") }, enabled = accounts.notices.isNotEmpty(), onClick = { management = false; clear = true })
            }
        }
    }
    if (clear) ConfirmAction("알림 삭제", "앱 내 알림 목록을 모두 삭제할까요?", { clear = false }) {
        accounts.notices.clear(); if (server != null) scope.launch { runCatching { withContext(Dispatchers.IO) { server.deleteAllNotifications() } } }; clear = false
    }
    if (accounts.notices.isEmpty()) StateCard("새로운 알림이 없어요", "새 소식이 도착하면 여기에 모아드릴게요.", Icons.Outlined.NotificationsNone)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    accounts.notices.toList().forEach { item -> key(item.id) {
        var options by remember { mutableStateOf(false) }
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (item.read) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = .4f))) {
        Row(Modifier.fillMaxWidth().semantics { stateDescription = if (item.read) "읽음" else "읽지 않음" }.clickable {
            val index = accounts.notices.indexOfFirst { it.id == item.id }
            if (index >= 0) accounts.notices[index] = item.copy(read = true)
            state.serverNoticeId(item.id)?.let { id -> if (server != null) scope.launch { runCatching { withContext(Dispatchers.IO) { server.readNotification(id) } } } }
            open(item)
        }.heightIn(min = 76.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(if (item.read) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(when (item.category) {
                    "DM" -> Icons.Outlined.ChatBubbleOutline
                    "메이트" -> Icons.Outlined.Groups
                    "커뮤니티" -> Icons.Outlined.FavoriteBorder
                    "운동" -> Icons.Outlined.FitnessCenter
                    else -> Icons.Outlined.NotificationsNone
                }, null, Modifier.size(18.dp), tint = if (item.read) FitMateColors.TextSecondary else MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.category, style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary)
                    if (!item.read) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
                Text(item.text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold)
            }
            Box {
                IconButton({ options = true }, Modifier.size(48.dp)) { Icon(Icons.Outlined.MoreHoriz, "${item.text} 옵션", Modifier.size(18.dp), tint = FitMateColors.TextSecondary) }
                DropdownMenu(options, { options = false }) {
                    DropdownMenuItem(text = { Text(if (item.read) "읽지 않음으로 표시" else "읽음으로 표시") }, onClick = {
                        val index = accounts.notices.indexOfFirst { it.id == item.id }
                        if (index >= 0) accounts.notices[index] = item.copy(read = !item.read)
                        options = false
                    })
                    DropdownMenuItem(text = { Text("삭제") }, onClick = { options = false; accounts.notices.removeAll { it.id == item.id } })
                }
            }
        }
        }
    } }
    }
    DebugOnly {
        var tools by remember { mutableStateOf(false) }
        TextButton({ tools = !tools }) { Text("개발 및 테스트 도구") }
        if (tools) {
            Text("서버/Push 없이 앱 내 알림을 생성합니다.")
            listOf(Triple("팔로우 요청", "커뮤니티", "FOLLOW"), Triple("좋아요·댓글", "커뮤니티", "COMMUNITY"),
                Triple("DM·메시지 요청", "DM", "DM"), Triple("메이트 신청·수락·거절·마감·일정 변경", "메이트", "MATE_LIST"),
                Triple("동기화 결과·리포트 갱신", "운동", "HISTORY"), Triple("신고 처리 결과", "안내", "REPORTS"), Triple("공지사항", "안내", "NOTICE")
            ).forEach { (label, category, route) -> TextButton({ accounts.notify(state, "$label 알림 예시", category, route) }) { Text(label) } }
        }
    }
    if (settings) SocialSheet({ settings = false }) {
        Text("알림 설정", style = MaterialTheme.typography.titleLarge)
        Text("앱 내 알림과 Push 설정 UI입니다. 실제 Push 전송·권한 요청은 미연결이며 설정은 앱 실행 중 유지됩니다.")
        ToggleLine("전체 Push", accounts.push) { accounts.push = it }
        accounts.preferences.forEach { (category, enabled) -> ToggleLine("$category Push", enabled) { accounts.preferences = accounts.preferences + (category to it) } }
        if (!accounts.push) Text("전체 Push가 꺼져 있어 종류별 설정은 전송에 적용되지 않습니다.")
    }
}

@Composable internal fun ReportTicketsFlow(modifier: Modifier, state: SocialState) = Screen(modifier) {
    if (state.accounts.reports.isEmpty()) Text("접수한 신고가 없어요.")
    var demo by remember { mutableStateOf(false) }
    DebugOnly { TextButton({ demo = !demo }) { Text("개발용 처리 결과 시뮬레이션") } }
    state.accounts.reports.toList().forEach { item -> SoftCard {
        Text(item.target); Text(item.reason); if (item.detail.isNotBlank()) Text(item.detail)
        Text("상태: ${item.status}"); Text(item.result)
        if (demo) Choices(listOf("RECEIVED", "REVIEWING", "RESOLVED", "REJECTED"), item.status) { status ->
            val i = state.accounts.reports.indexOfFirst { it.id == item.id }
            if (i >= 0) state.accounts.reports[i] = item.copy(status = status, result = "처리 결과 예시 · 실제 운영자 판단이 아닙니다.")
            state.accounts.notify(state, "신고 상태 $status (예시)", "안내", "REPORTS")
        }
    } }
}

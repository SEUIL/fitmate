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

internal data class MemberPreview(val name: String, val private: Boolean = false, val bio: String = "함께 꾸준히 운동해요.")
internal data class AppNotice(val id: Long, val text: String, val category: String, val destination: String, val target: String? = null, val read: Boolean = false)
internal data class ReportTicket(val id: Long, val target: String, val reason: String, val detail: String, val status: String = "RECEIVED", val result: String = "서버 전송 전 로컬 접수")
internal class CommunityAccounts {
    val people = listOf(MemberPreview("민준"), MemberPreview("수연"), MemberPreview("지현"), MemberPreview("비공개 메이트", true), MemberPreview("요청 메이트", true))
    var opened by mutableStateOf<String?>(null)
    var directory by mutableStateOf<String?>(null)
    var followers by mutableStateOf(setOf("민준"))
    var incoming by mutableStateOf(mapOf("요청 메이트" to "PENDING"))
    var outgoing by mutableStateOf(emptyMap<String, String>())
    var photo by mutableStateOf<String?>(null)
    var reportTarget by mutableStateOf<String?>(null)
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

@Composable internal fun CommunityAccountSheets(state: SocialState, dm: (ChatRoom) -> Unit) {
    val accounts = state.accounts
    accounts.directory?.let { mode -> SocialSheet({ accounts.directory = null }) {
        Text(mode, style = MaterialTheme.typography.titleLarge)
        Choices(listOf("사용자", "팔로워", "팔로잉", "팔로우 요청"), mode) { accounts.directory = it }
        val names = when (mode) {
            "팔로워" -> accounts.followers.toList(); "팔로잉" -> state.following.toList()
            "팔로우 요청" -> accounts.incoming.keys.toList(); else -> accounts.people.map { it.name }
        }.filterNot { it in state.blocked }
        if (names.isEmpty()) Text("아직 표시할 사용자가 없어요.")
        names.forEach { name -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ accounts.opened = name; accounts.directory = null }, Modifier.weight(1f)) { Text(name) }
            if (mode == "팔로우 요청") {
                if (accounts.incoming[name] == "PENDING") {
                    TextButton({ accounts.respond(state, name, true) }) { Text("수락") }
                    TextButton({ accounts.respond(state, name, false) }) { Text("거절") }
                } else Text(accounts.incoming[name].orEmpty())
            } else if (mode == "팔로워") TextButton({ accounts.followers = accounts.followers - name; accounts.reconcile(state) }) { Text("삭제") }
        } }
        if (mode == "팔로우 요청") DebugOnly {
            TextButton({ accounts.incoming = accounts.incoming + ("요청 메이트" to "PENDING") }) { Text("테스트: 요청 1건 받기") }
        }
    } }
    accounts.reportTarget?.let { target -> SocialSheet({ accounts.reportTarget = null }) {
        var reason by remember(target) { mutableStateOf("부적절한 콘텐츠") }
        var detail by remember(target) { mutableStateOf("") }
        Text("신고", style = MaterialTheme.typography.titleLarge)
        Text(target)
        Choices(listOf("부적절한 콘텐츠", "욕설 또는 괴롭힘", "광고 또는 스팸", "사칭", "노쇼 또는 반복적인 일정 취소", "기타"), reason) { reason = it }
        OutlinedTextField(detail, { detail = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("상세 사유 (선택)") })
        Text("서버 전송 없이 이 앱 실행 중에만 접수 상태를 보관합니다.", style = MaterialTheme.typography.bodySmall)
        PrimaryAction("로컬 신고 접수") {
            accounts.reports.add(0, ReportTicket(state.nextId(), target, reason, detail))
            accounts.notify(state, "신고를 로컬 접수했어요.", "안내", "REPORTS")
            accounts.reportTarget = null
        }
    } }
}

@Composable internal fun MemberProfileScreen(modifier: Modifier, state: SocialState, back: () -> Unit, dm: (ChatRoom) -> Unit) = Screen(modifier) {
    val accounts = state.accounts
    accounts.opened?.let { name ->
        val person = accounts.people.firstOrNull { it.name == name } ?: MemberPreview(name)
        var tab by remember(name) { mutableStateOf("게시물") }
        var options by remember(name) { mutableStateOf(false) }
        var information by remember(name) { mutableStateOf<String?>(null) }
        var selected by remember(name) { mutableStateOf<FeedEntry?>(null) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
            Box(Modifier.weight(1f)) { SocialTitle(name, Icons.Outlined.MoreHoriz, "사용자 옵션") { options = true } }
        }
        ProfileIdentityRow(name, null, listOf("—", "—", "—")) {
            if (it == "운동 기록") tab = it else information = "상대방의 $it 정보는 서버 연결 후 제공됩니다."
        }
        Text(name, fontWeight = FontWeight.SemiBold)
        if (name in state.blocked) {
            Text("차단한 사용자입니다. 프로필과 게시물이 숨겨져 있어요.")
            TextButton({ state.blocked = state.blocked - name }) { Text("차단 해제") }
        } else {
            if (accounts.visibleProfile(state, name)) Text(person.bio, style = MaterialTheme.typography.bodyMedium)
            Text(if (person.private) "비공개 계정" else "공개 계정", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ accounts.follow(state, name) }, Modifier.weight(1f), shape = FitMateShapes.Button) { Text(when { name in state.following -> "팔로잉"; accounts.outgoing[name] == "PENDING" -> "요청 취소"; else -> "팔로우" }, color = FitMateColors.TextPrimary) }
                OutlinedButton({
                    val room = state.chat(name)
                    room.status = if (person.private && name !in state.mutual) "요청 대기" else "대화 가능"
                    dm(room)
                }, Modifier.weight(1f), shape = FitMateShapes.Button, enabled = accounts.canDm(state, name)) { Text(if (person.private && name !in state.mutual) "DM 요청" else "메시지") }
            }
            if (!accounts.visibleProfile(state, name)) Text("팔로우가 승인되면 프로필 상세를 볼 수 있어요.")
            val entries = state.feed.filter { it.post.authorName == name && (it.visibility == "전체 공개" || name in state.mutual) }
            Column(verticalArrangement = Arrangement.spacedBy(if (tab == "게시물") 3.dp else 12.dp)) {
            SocialTabs(listOf("게시물", "운동 기록"), tab) { tab = it }
            if (tab == "게시물") {
                if (entries.isEmpty()) Text("표시할 게시물이 없습니다.", Modifier.padding(vertical = 32.dp), color = FitMateColors.TextSecondary)
                ProfilePostGrid(entries) { selected = it }
            } else {
                Text("공개된 운동 기록", style = MaterialTheme.typography.titleSmall)
                if (entries.none { it.workout != null }) Text("공개 게시물에 첨부된 운동 기록이 없어요.", Modifier.padding(vertical = 24.dp), color = FitMateColors.TextSecondary)
                entries.filter { it.workout != null }.forEach { entry -> OutlinedCard(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(entry.workout.orEmpty()); Text(entry.post.createdAt, style = MaterialTheme.typography.bodySmall) }
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
        if (options) SocialSheet({ options = false }) {
            Text("사용자 옵션", style = MaterialTheme.typography.titleLarge)
            TextButton({ options = false; accounts.reportTarget = "프로필: $name" }) { Text("프로필 신고") }
            TextButton({ state.blocked = state.blocked + name; state.following = state.following - name; accounts.followers = accounts.followers - name; accounts.reconcile(state); options = false }) { Text("차단") }
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

@Composable internal fun NotificationFlow(modifier: Modifier, state: SocialState, open: (AppNotice) -> Unit) = Screen(modifier) {
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
                    management = false
                })
                DropdownMenuItem(text = { Text("전체 삭제") }, enabled = accounts.notices.isNotEmpty(), onClick = { management = false; clear = true })
            }
        }
    }
    if (clear) ConfirmAction("알림 삭제", "앱 내 알림 목록을 모두 삭제할까요?", { clear = false }) { accounts.notices.clear(); clear = false }
    if (accounts.notices.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(32.dp), tint = FitMateColors.TextSecondary)
        Text("새로운 알림이 없어요", style = MaterialTheme.typography.titleSmall)
        Text("새 소식이 도착하면 여기에 모아드릴게요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    }
    Column(Modifier.fillMaxWidth()) {
    accounts.notices.toList().forEach { item -> key(item.id) {
        var options by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().semantics { stateDescription = if (item.read) "읽음" else "읽지 않음" }.clickable {
            val index = accounts.notices.indexOfFirst { it.id == item.id }
            if (index >= 0) accounts.notices[index] = item.copy(read = true)
            open(item)
        }.heightIn(min = 76.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
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
        if (item.id != accounts.notices.lastOrNull()?.id) HorizontalDivider(Modifier.padding(start = 48.dp), color = FitMateColors.Outline.copy(alpha = .5f))
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

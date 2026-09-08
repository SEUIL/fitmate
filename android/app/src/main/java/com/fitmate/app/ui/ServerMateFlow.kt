package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.util.UUID

internal class MateServerState {
    var page by mutableStateOf("목록")
    var postId by mutableStateOf<String?>(null)
    var roomId by mutableStateOf<String?>(null)
    var chatOrigin by mutableStateOf("상세")
    var busy by mutableStateOf(false)
    fun back(): Boolean {
        if (busy) return true
        if (page == "목록") return false
        page = if (page == "채팅") chatOrigin else "목록"
        return true
    }
}

private fun JsonObject.value(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.objectOrNull(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
private fun applicationLabel(status: String) = when (status) {
    "PENDING" -> "신청 대기"; "ACCEPTED" -> "참가 확정"; "REJECTED" -> "신청 거절"
    "CANCELED" -> "신청 취소"; "WITHDRAWN" -> "참가 취소"; else -> status
}
private fun postLabel(status: String) = when (status) {
    "RECRUITING" -> "모집 중"; "FULL" -> "정원 마감"; "CLOSED" -> "모집 마감"; "CANCELED" -> "모임 취소"; else -> status
}

@Composable internal fun ServerMateFlow(
    modifier: Modifier, api: FitMateApiClient, state: MateServerState, profile: UserProfile,
    userId: String, postsChanged: (List<MatePost>) -> Unit
) = with(state) {
    val server = remember(api) { ServerMateApi(api) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var error by remember(page, postId, roomId) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var posts by remember { mutableStateOf(emptyList<MatePost>()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var detail by remember(postId) { mutableStateOf<JsonObject?>(null) }
    var applicants by remember(postId) { mutableStateOf(emptyList<JsonObject>()) }
    var rooms by remember { mutableStateOf(emptyList<JsonObject>()) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block(); revision++ }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "요청에 실패했습니다. 새로고침 후 다시 확인하세요."; revision++ }
            finally { busy = false }
        }
    }
    BackHandler(page != "목록" || busy) { back() }
    confirm?.let { (title, run) -> ConfirmAction(title, "계속 진행할까요?", { confirm = null }) { confirm = null; run() } }
    LaunchedEffect(page, postId, revision) {
        if (page == "채팅" || page == "작성") return@LaunchedEffect
        loading = true
        try {
            when (page) {
                "목록" -> {
                    val result = withContext(Dispatchers.IO) { server.page() }
                    posts = result.getAsJsonArray("items").map { toMatePost(it.asJsonObject) }
                    nextCursor = if (result.get("hasNext").asBoolean) result.value("nextCursor").ifBlank { null } else null
                    postsChanged(posts)
                }
                "상세" -> {
                    detail = null; applicants = emptyList()
                    val result = withContext(Dispatchers.IO) { server.detail(requireNotNull(postId)) }
                    detail = result
                    if (result.value("authorId") == userId) applicants = withContext(Dispatchers.IO) { server.applications(requireNotNull(postId)) }
                }
                "문의 목록" -> rooms = withContext(Dispatchers.IO) { server.rooms() }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "서버 데이터를 불러오지 못했습니다." }
        finally { loading = false }
    }
    DisposableEffect(Unit) { onDispose { busy = false } }
    when (page) {
        "작성" -> MateWriteScreen(modifier, api, profile.activityRegionId, profile.region) { draft, region, key ->
            busy = true
            try {
                val body = mateCreateBody(draft, region)
                val created = withContext(Dispatchers.IO) { server.create(body, key) }
                postId = created.value("id"); page = "상세"
            } finally { busy = false }
        }
        "채팅" -> ServerMateChat(modifier, server, requireNotNull(roomId), userId)
        else -> Screen(modifier) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (page == "목록") "같이 운동하기" else if (page == "문의 목록") "메이트 문의" else "모집 상세", style = MaterialTheme.typography.titleLarge)
                TextButton({ error = null; revision++ }, enabled = !busy && !loading) { Text("새로고침") }
            }
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ErrorText(error)
            when (page) {
                "목록" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ page = "작성" }) { Text("모집 글 작성") }
                        OutlinedButton({ page = "문의 목록" }) { Text("문의 채팅") }
                    }
                    var sport by remember { mutableStateOf("전체") }
                    Choices(listOf("전체") + Sport.entries.map { it.label }, sport) { sport = it }
                    val visible = posts.filter { sport == "전체" || it.sport.label == sport }
                    if (!loading && error == null && visible.isEmpty()) Text("표시할 모집 글이 없어요.")
                    visible.forEach { post ->
                        OutlinedCard(onClick = { postId = requireNotNull(post.serverId); page = "상세" }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(post.title, fontWeight = FontWeight.SemiBold)
                                Text("${post.sport.label} · ${post.dateTime} · ${post.duration}", style = MaterialTheme.typography.bodySmall)
                                Text(post.details.location, style = MaterialTheme.typography.bodySmall)
                                Text("${post.participantCount}/${post.capacity}명 · ${postLabel(post.serverStatus.orEmpty())}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    nextCursor?.let { cursor -> OutlinedButton({
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) { server.page(cursor) }
                                    posts = (posts + result.getAsJsonArray("items").map { toMatePost(it.asJsonObject) }).distinctBy { it.serverId }
                                    nextCursor = if (result.get("hasNext").asBoolean) result.value("nextCursor").ifBlank { null } else null
                                    postsChanged(posts)
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy) { Text("모집 글 더 보기") } }
                }
                "문의 목록" -> {
                    if (!loading && error == null && rooms.isEmpty()) Text("아직 메이트 문의가 없어요.")
                    rooms.forEach { room ->
                        val other = room.objectOrNull(if (room.objectOrNull("author")?.value("userId") == userId) "inquirer" else "author")
                        OutlinedCard(onClick = { roomId = room.value("id"); chatOrigin = "문의 목록"; page = "채팅" }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(other?.value("nickname").orEmpty().ifBlank { "메이트" }, fontWeight = FontWeight.SemiBold)
                                Text(room.objectOrNull("lastMessage")?.value("body").orEmpty().ifBlank { "대화를 시작해 보세요." }, maxLines = 2)
                                Text("읽지 않은 메시지 ${room.value("unreadCount")}개", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                "상세" -> detail?.let { row ->
                    val post = toMatePost(row)
                    val mine = row.objectOrNull("myApplication")
                    val owner = row.value("authorId") == userId
                    val actions = row.getAsJsonArray("allowedActions").map { it.asString }.toSet()
                    Text(post.title, style = MaterialTheme.typography.titleLarge)
                    Text("${postLabel(post.serverStatus.orEmpty())} · ${post.participantCount}/${post.capacity}명")
                    SoftCard {
                        Text("${post.sport.label} · ${post.dateTime} · ${post.duration}")
                        Text(post.description)
                        Text("공개 장소 · ${post.details.location}")
                        Text("예상 비용 · ${post.details.cost}원 · ${post.details.visibility}")
                        Text("${post.difficulty}${if(post.isBeginnerWelcome) " · 초보 환영" else ""}")
                        post.details.sportFields.filterValues { it.isNotBlank() }.forEach { (name, value) -> Text("$name · $value") }
                        Text(if (post.details.meetingPoint.isNotBlank()) "집결 위치 · ${post.details.meetingPoint}" else "집결 위치는 작성자·참가 확정자에게만 공개됩니다.")
                    }
                    if (owner) {
                        Text("참가 신청 관리", fontWeight = FontWeight.Bold)
                        applicants.filter { it.value("userId") != userId }.forEach { applicant ->
                            SoftCard {
                                Text("${applicant.objectOrNull("applicant")?.value("nickname")} · ${applicationLabel(applicant.value("status"))}")
                                if (applicant.value("status") == "PENDING" && post.serverStatus == "RECRUITING") Row {
                                    listOf("accept" to "수락", "reject" to "거절").forEach { (command, label) ->
                                        TextButton({ action { withContext(Dispatchers.IO) { server.decide(requireNotNull(postId), applicant.value("id"), command, applicant.get("version").asLong) } } }, enabled = !busy) { Text(label) }
                                    }
                                }
                            }
                        }
                        if (applicants.none { it.value("status") == "PENDING" }) Text("대기 중인 신청이 없습니다.")
                        if (post.serverStatus in setOf("RECRUITING", "FULL")) {
                            var editing by remember(postId) { mutableStateOf(false) }
                            var title by remember(row) { mutableStateOf(post.title) }
                            var body by remember(row) { mutableStateOf(post.description) }
                            TextButton({ editing = !editing }, enabled = !busy) { Text("제목·안내 수정") }
                            if (editing) {
                                OutlinedTextField(title, { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(body, { body = it }, label = { Text("안내") }, modifier = Modifier.fillMaxWidth())
                                Button({ action { withContext(Dispatchers.IO) { server.update(requireNotNull(postId), mapOf("version" to row.get("version").asLong, "title" to title, "body" to body)) }; editing = false } }, enabled = !busy && title.isNotBlank() && body.isNotBlank()) { Text("수정 저장") }
                            }
                            OutlinedButton({ confirm = "모집 마감" to { action { withContext(Dispatchers.IO) { server.action(requireNotNull(postId), "close", row.get("version").asLong) } } } }, enabled = !busy) { Text("모집 마감") }
                        }
                        if (post.serverStatus != "CANCELED") TextButton({ confirm = "모임 취소" to { action { withContext(Dispatchers.IO) { server.action(requireNotNull(postId), "cancel", row.get("version").asLong) } } } }, enabled = !busy) { Text("모임 취소") }
                        OutlinedButton({ page = "문의 목록" }) { Text("받은 문의 채팅") }
                    } else {
                        mine?.let { Text("내 상태 · ${applicationLabel(it.value("status"))}") }
                        if ("APPLY" in actions) Button({ action { withContext(Dispatchers.IO) { server.apply(requireNotNull(postId)) } } }, enabled = !busy) { Text("참가 신청") }
                        if (mine?.value("status") in setOf("PENDING", "ACCEPTED")) OutlinedButton({
                            confirm = "참가 취소" to { action { withContext(Dispatchers.IO) { server.decide(requireNotNull(postId), requireNotNull(mine).value("id"), "cancel", mine.get("version").asLong) } } }
                        }, enabled = !busy) { Text("참가 취소") }
                        if (row.value("myInquiryRoomId").isNotBlank() || "OPEN_INQUIRY_ROOM" in actions) OutlinedButton({
                            action {
                                roomId = row.value("myInquiryRoomId").ifBlank { withContext(Dispatchers.IO) { server.openRoom(requireNotNull(postId)).value("id") } }
                                chatOrigin = "상세"; page = "채팅"
                            }
                        }, enabled = !busy) { Text("작성자에게 문의") }
                    }
                }
            }
        }
    }
}

@Composable private fun ServerMateChat(modifier: Modifier, server: ServerMateApi, id: String, user: String) {
    val scope = rememberCoroutineScope()
    var room by remember(id) { mutableStateOf<JsonObject?>(null) }
    var messages by remember(id) { mutableStateOf(emptyList<JsonObject>()) }
    var draft by remember(id) { mutableStateOf("") }
    var pending by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, refresh) {
        while (isActive) {
            try {
                val current = withContext(Dispatchers.IO) { server.room(id) }
                var after = messages.lastOrNull()?.get("sequenceNo")?.asLong ?: 0L
                val added = mutableListOf<JsonObject>()
                do {
                    val page = withContext(Dispatchers.IO) { server.messages(id, after) }
                    added += page
                    if (page.isEmpty()) break
                    val next = page.last().get("sequenceNo").asLong
                    check(next > after) { "메시지 순서를 확인할 수 없습니다." }; after = next
                } while (page.size == 100)
                messages = (messages + added).distinctBy { it.value("id") }.sortedBy { it.get("sequenceNo").asLong }
                room = current
                messages.lastOrNull()?.let { last ->
                    if (last.get("sequenceNo").asLong > current.get("myLastReadSequence").asLong)
                        withContext(Dispatchers.IO) { server.read(id, last.value("id")) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message; return@LaunchedEffect }
            delay(3_000)
        }
    }
    Screen(modifier) {
        Text("메이트 문의", style = MaterialTheme.typography.titleLarge)
        ErrorText(error)
        if (error != null) TextButton({ error = null; refresh++ }) { Text("다시 연결") }
        if (room == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        messages.forEach { message ->
            val mine = message.value("senderId") == user
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Surface(shape = MaterialTheme.shapes.medium, color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.widthIn(max = 280.dp).padding(12.dp)) {
                        Text(message.value("body"))
                        if (mine) {
                            val peerRead = room?.get(if (room?.objectOrNull("author")?.value("userId") == user) "inquirerLastReadSequence" else "hostLastReadSequence")?.asLong ?: 0L
                            Text(if (message.get("sequenceNo").asLong <= peerRead) "읽음" else "전송됨", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        val writable = room?.get("writable")?.asBoolean == true
        if (room != null && !writable) Text("종료된 모임·신청 또는 접근 제한으로 읽기만 가능합니다.")
        OutlinedTextField(draft, { if (!sending) { draft = it; pending = null } }, Modifier.fillMaxWidth(), label = { Text("메시지") }, enabled = writable && !sending, maxLines = 5)
        Button({
            if (!sending) {
                val attempt = pending ?: (UUID.randomUUID().toString() to draft.trim()).also { pending = it }
                sending = true; error = null
                scope.launch {
                    try {
                        val sent = withContext(Dispatchers.IO) { server.send(id, attempt.second, attempt.first) }
                        messages = (messages + sent).distinctBy { it.value("id") }.sortedBy { it.get("sequenceNo").asLong }
                        draft = ""; pending = null; refresh++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "전송하지 못했습니다. 다시 누르면 같은 메시지를 재확인합니다." }
                    finally { sending = false }
                }
            }
        }, enabled = writable && !sending && draft.isNotBlank() && draft.length <= 2000) { Text(if (sending) "전송 중…" else if (pending != null) "다시 전송" else "전송") }
    }
}

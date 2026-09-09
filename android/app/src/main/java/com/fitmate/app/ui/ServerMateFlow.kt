package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
        page = if (page == "채팅") chatOrigin else if (page == "수정") "상세" else "목록"
        return true
    }
}

private fun JsonObject.value(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.objectOrNull(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
private fun applicationLabel(status: String) = when (status) {
    "PENDING" -> "신청 대기"; "ACCEPTED" -> "참가 확정"; "REJECTED" -> "신청 거절"
    "CANCELED" -> "신청 취소"; "WITHDRAWN" -> "참가 취소"; else -> status
}
internal fun postLabel(status: String) = when (status) {
    "RECRUITING" -> "모집 중"; "FULL" -> "정원 마감"; "CLOSED" -> "모집 마감"; "CANCELED" -> "모임 취소"; else -> status
}
internal fun sportFilterCode(label: String) = when (label) {
    "헬스" -> "HEALTH"; "러닝" -> "RUNNING"; "배드민턴" -> "BADMINTON"; "클라이밍" -> "CLIMBING"; "등산" -> "HIKING"; "골프" -> "GOLF"; else -> "전체"
}
internal fun sportFilterLabel(code: String) = when (code) {
    "HEALTH" -> "헬스"; "RUNNING" -> "러닝"; "BADMINTON" -> "배드민턴"; "CLIMBING" -> "클라이밍"; "HIKING" -> "등산"; "GOLF" -> "골프"; else -> "전체"
}
internal fun difficultyFilterCode(label: String) = when (label) { "초보" -> "BEGINNER"; "중급" -> "INTERMEDIATE"; "상급" -> "ADVANCED"; else -> "전체" }
internal fun difficultyFilterLabel(code: String) = when (code) { "BEGINNER" -> "초보"; "INTERMEDIATE" -> "중급"; "ADVANCED" -> "상급"; else -> "전체" }

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
    var filterSport by remember { mutableStateOf("전체") }
    var filterDifficulty by remember { mutableStateOf("전체") }
    var filterStatus by remember { mutableStateOf("RECRUITING") }
    var filterRegion by remember { mutableStateOf("") }
    var filterRegionLabel by remember { mutableStateOf("전체 지역") }
    var filterDate by remember { mutableStateOf("전체 기간") }
    val dateStart = when (filterDate) {
        "오늘" -> java.time.LocalDate.now()
        "내일" -> java.time.LocalDate.now().plusDays(1)
        "7일 이내" -> java.time.LocalDate.now()
        else -> null
    }
    val from = dateStart?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toString()
    val to = dateStart?.plusDays(if (filterDate == "7일 이내") 7 else 1)?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.minusNanos(1)?.toString()
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
    LaunchedEffect(page, postId, revision, filterSport, filterDifficulty, filterStatus, filterRegion, filterDate) {
        if (page in setOf("채팅", "작성", "수정", "필터")) return@LaunchedEffect
        loading = true
        try {
            when (page) {
                "목록" -> {
                    posts = emptyList()
                    nextCursor = null
                    val result = withContext(Dispatchers.IO) {
                        server.page(
                            sportCode = filterSport.takeUnless { it == "전체" },
                            difficulty = filterDifficulty.takeUnless { it == "전체" },
                            status = filterStatus.takeUnless { it == "전체" },
                            regionId = filterRegion.ifBlank { null }, from = from, to = to,
                        )
                    }
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
        "필터" -> MateFilterPage(modifier, api, MateFilters(filterSport, filterDifficulty, filterStatus, filterRegion, filterRegionLabel, filterDate), { back() }) { selected ->
            filterSport = selected.sport; filterDifficulty = selected.difficulty; filterStatus = selected.status
            filterRegion = selected.region; filterRegionLabel = selected.regionLabel; filterDate = selected.date
            page = "목록"
        }
        "수정" -> detail?.let { original ->
            MateWriteScreen(modifier, api, original.value("regionId"), original.value("locationSummary"), initialPost = original) { draft, region, _ ->
                busy = true
                try {
                    val payload = mateUpdateBody(original, draft, region)
                    require(applicants.isEmpty() || payload.keys.all { it in setOf("version", "title", "body", "capacity") }) {
                        "신청 이력이 있는 모집글은 제목·안내·인원만 수정할 수 있어요. 나머지 조건은 기존 값으로 유지해 주세요."
                    }
                    withContext(Dispatchers.IO) { server.update(requireNotNull(postId), payload) }
                    page = "상세"
                } finally { busy = false }
            }
        }
        "작성" -> MateWriteScreen(modifier, api, profile.activityRegionId, profile.region) { draft, region, key ->
            busy = true
            try {
                val body = mateCreateBody(draft, region)
                val created = withContext(Dispatchers.IO) { server.create(body, key) }
                postId = created.value("id"); page = "상세"
            } finally { busy = false }
        }
        "채팅" -> ServerMateChat(modifier, server, requireNotNull(roomId), userId)
        else -> Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Screen(Modifier.fillMaxSize(), background = MaterialTheme.colorScheme.background) {
            MateHeader(if (page == "목록") "같이 운동하기" else if (page == "문의 목록") "문의함" else "모임 상세", if (page != "목록") ({ back(); Unit }) else null) {
                if (page == "목록") TextButton({ page = "문의 목록" }) { Text("문의함") }
                IconButton({ error = null; revision++ }, enabled = !busy && !loading) { Icon(Icons.Outlined.Refresh, "새로고침") }
            }
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ErrorText(error)
            when (page) {
                "목록" -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ page = "필터" }, Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { Text(filterRegionLabel, maxLines = 1) }
                        OutlinedButton({ page = "필터" }, Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { Text(filterDate, maxLines = 1) }
                        IconButton({ page = "필터" }) { Icon(Icons.Outlined.Tune, "필터") }
                    }
                    MateSportTabs(sportFilterLabel(filterSport)) { filterSport = sportFilterCode(it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (filterStatus == "RECRUITING") "모집 중인 모임" else "함께할 모임", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("최신순", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!loading && error == null && posts.isEmpty()) SoftCard {
                        Text("조건에 맞는 모임이 아직 없어요.", fontWeight = FontWeight.SemiBold)
                        Text("지역이나 날짜를 넓히거나 직접 모임을 만들어 보세요.", style = MaterialTheme.typography.bodyMedium)
                        TextButton({ page = "필터" }) { Text("조건 바꾸기") }
                    }
                    posts.forEach { post ->
                        MateListingCard(post, postLabel(post.serverStatus.orEmpty())) { postId = requireNotNull(post.serverId); page = "상세" }
                    }
                    nextCursor?.let { cursor -> OutlinedButton({
                        if (!busy && !loading) {
                            busy = true
                            val requestedFilters = Triple(filterSport, filterDifficulty, filterStatus)
                            val requestedRange = Triple(filterRegion, from, to)
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) { server.page(cursor, requestedFilters.first.takeUnless { it == "전체" }, requestedFilters.second.takeUnless { it == "전체" }, requestedFilters.third.takeUnless { it == "전체" }, requestedRange.first.ifBlank { null }, requestedRange.second, requestedRange.third) }
                                    if (page != "목록" || requestedFilters != Triple(filterSport, filterDifficulty, filterStatus) || requestedRange != Triple(filterRegion, from, to)) return@launch
                                    posts = (posts + result.getAsJsonArray("items").map { toMatePost(it.asJsonObject) }).distinctBy { it.serverId }
                                    nextCursor = if (result.get("hasNext").asBoolean) result.value("nextCursor").ifBlank { null } else null
                                    postsChanged(posts)
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy && !loading) { Text("모집 글 더 보기") } }
                    Spacer(Modifier.height(80.dp))
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
                    MateDetailSummary(post, postLabel(post.serverStatus.orEmpty()))
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
                            TextButton({ page = "수정" }, enabled = !busy) { Text("모집 정보 수정") }
                            OutlinedButton({ confirm = "모집 마감" to { action { withContext(Dispatchers.IO) { server.action(requireNotNull(postId), "close", row.get("version").asLong) } } } }, enabled = !busy) { Text("모집 마감") }
                        }
                        if (post.serverStatus != "CANCELED") TextButton({ confirm = "모임 취소" to { action { withContext(Dispatchers.IO) { server.action(requireNotNull(postId), "cancel", row.get("version").asLong) } } } }, enabled = !busy) { Text("모임 취소") }
                        OutlinedButton({ page = "문의 목록" }) { Text("받은 문의 채팅") }
                    } else {
                        mine?.let { Text("내 상태 · ${applicationLabel(it.value("status"))}") }
                    }
                }
            }
        }
        if (page == "목록") {
            Button({ page = "작성" }, colors = mateButtonColors(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp).heightIn(min = 50.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("모임 만들기")
            }
        }
        }
        if (page == "상세") detail?.takeIf { it.value("authorId") != userId }?.let { row ->
            val mine = row.objectOrNull("myApplication")
            val actions = row.getAsJsonArray("allowedActions").map { it.asString }.toSet()
            MateActionBar {
                if (row.value("myInquiryRoomId").isNotBlank() || "OPEN_INQUIRY_ROOM" in actions) OutlinedButton({
                    action {
                        roomId = row.value("myInquiryRoomId").ifBlank { withContext(Dispatchers.IO) { server.openRoom(requireNotNull(postId)).value("id") } }
                        chatOrigin = "상세"; page = "채팅"
                    }
                }, Modifier.weight(1f).heightIn(min = 52.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), enabled = !busy) { Text("문의하기") }
                if (mine?.value("status") in setOf("PENDING", "ACCEPTED")) {
                    OutlinedButton({
                        confirm = "참가 취소" to { action { withContext(Dispatchers.IO) { server.decide(requireNotNull(postId), requireNotNull(mine).value("id"), "cancel", mine.get("version").asLong) } } }
                    }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = !busy) { Text("참가 취소") }
                } else Button({ action { withContext(Dispatchers.IO) { server.apply(requireNotNull(postId)) } } },
                    Modifier.weight(1.5f).heightIn(min = 52.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), colors = mateButtonColors(), enabled = !busy && "APPLY" in actions) {
                    Text(if ("APPLY" in actions) "참가 신청" else "신청할 수 없어요")
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

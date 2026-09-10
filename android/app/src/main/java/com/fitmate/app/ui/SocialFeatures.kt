package com.fitmate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.*
import com.fitmate.app.data.remote.ServerSocialApi
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal data class FeedComment(val id: Long, val author: String, val text: String, val mine: Boolean)
internal data class ServerMemberProfile(
    val userId: String,
    val nickname: String,
    val displayName: String,
    val bio: String?,
    val profileImageUrl: String?,
    val isPrivate: Boolean,
    val followerCount: Long,
    val followingCount: Long,
    val canViewProfile: Boolean,
    val followingStatus: String?,
)
internal data class FeedEntry(
    val post: CommunityPost, val mine: Boolean = false, val visibility: String = "전체 공개",
    val photo: String? = null, val workout: String? = null,
    val comments: List<FeedComment> = emptyList(),
    val photos: List<String>? = null
)
internal fun FeedEntry.photoList(): List<String> = photos ?: listOfNotNull(photo)
internal data class ChatEntry(
    val id: Long, val text: String, val mine: Boolean, val photo: String? = null,
    val state: String = "전송 완료", val read: Boolean = false,
    val time: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val failOnce: Boolean = false
)
internal class ChatRoom(val id: Long, val name: String, initial: List<ChatEntry>, initialStatus: String = "대화 가능", val incoming: Boolean = true) {
    var messages by mutableStateOf(initial)
    var status by mutableStateOf(initialStatus)
    var unread by mutableStateOf(initial.count { !it.mine })
    var muted by mutableStateOf(false)
    var left by mutableStateOf(false)
    var failNextImage by mutableStateOf(false)
    var draft by mutableStateOf("")
    var photo by mutableStateOf<String?>(null)
    var requestSent by mutableStateOf(initialStatus == "요청 대기" && initial.any { it.mine })
}
internal class SocialState(posts: List<CommunityPost>, conversations: List<DmConversation>) {
    private val serverPostIds = mutableMapOf<Long, String>()
    private val serverPostVersions = mutableMapOf<Long, Long>()
    private val serverCommentIds = mutableMapOf<Long, String>()
    private val serverNoticeIds = mutableMapOf<Long, String>()
    private val serverConversationIds = mutableMapOf<Long, String>()
    private val serverConversationOtherUsers = mutableMapOf<Long, String>()
    private val serverUserIdsByNickname = mutableMapOf<String, String>()
    var serverProfiles by mutableStateOf<Map<String, ServerMemberProfile>>(emptyMap())
    val accounts = CommunityAccounts()
    val communityTab = mutableStateOf("전체 공개")
    val composerText = mutableStateOf("")
    val composerPhoto = mutableStateOf<String?>(null)
    val composerPhotos = mutableStateOf<List<String>?>(null)
    val composerWorkout = mutableStateOf<String?>(null)
    val composerVisibility = mutableStateOf("전체 공개")
    val composerEditing = mutableStateOf<Long?>(null)
    var openComposer by mutableStateOf(false)
    fun clearComposer() {
        composerText.value = ""; composerPhoto.value = null; composerWorkout.value = null
        composerPhotos.value = null
        composerVisibility.value = "전체 공개"; composerEditing.value = null
    }
    private var serial = 10000L
    fun nextId() = ++serial
    internal fun restoreSerial(value: Long) { serial = maxOf(serial, value) }
    internal fun currentSerial() = serial
    val feed = mutableStateListOf<FeedEntry>().apply {
        addAll(posts.map { item -> FeedEntry(item, comments = List(item.commentCount) { i -> FeedComment(nextId(), "메이트 ${i + 1}", "오늘도 응원해요!", false) }) })
    }
    fun replaceServerFeed(rows: List<JsonObject>, mineId: String, append: Boolean = false) {
        if (!append) {
            feed.clear(); serverUserIdsByNickname.clear()
            serverPostIds.clear(); serverPostVersions.clear(); serverCommentIds.clear()
        }
        rows.forEach { row ->
            val id = row.text("postId").hashCode().toLong()
            serverPostIds[id] = row.text("postId")
            serverPostVersions[id] = row.get("version")?.asLong ?: 0L
            val author = row.getAsJsonObject("author")
            val name = author.text("nickname")
            serverUserIdsByNickname[name] = author.text("userId")
            val entry = FeedEntry(
                CommunityPost(id, name, row.text("content"), row.get("likedByMe")?.asBoolean ?: false,
                    row.get("likeCount")?.asInt ?: 0, row.get("commentCount")?.asInt ?: 0, row.text("createdAt")),
                mine = author.text("userId") == mineId,
                visibility = if (row.text("visibility") == "MUTUAL") "맞팔 공개" else "전체 공개",
                photo = row.getAsJsonArray("mediaUrls")?.firstOrNull()?.asString,
                photos = row.getAsJsonArray("mediaUrls")?.map { it.asString }.orEmpty(),
            )
            val existing = feed.indexOfFirst { it.post.id == id }
            if (existing >= 0) feed[existing] = entry else feed += entry
        }
    }
    fun serverPostId(localId: Long): String? = serverPostIds[localId]
    fun localPostId(serverId: String?): Long? = serverId?.let { value -> serverPostIds.entries.firstOrNull { it.value == value }?.key }
    fun serverPostVersion(localId: Long): Long? = serverPostVersions[localId]
    fun rememberServerProfile(row: JsonObject): ServerMemberProfile {
        val relationship = row.getAsJsonObject("relationship")
        val profile = ServerMemberProfile(
            userId = row.text("userId"), nickname = row.text("nickname"), displayName = row.text("displayName"),
            bio = row.text("bio").ifBlank { null }, profileImageUrl = row.text("profileImageUrl").ifBlank { null }, isPrivate = row.text("visibility") == "PRIVATE",
            followerCount = row.get("followerCount")?.asLong ?: 0L, followingCount = row.get("followingCount")?.asLong ?: 0L,
            canViewProfile = row.get("canViewProfile")?.asBoolean ?: true,
            followingStatus = relationship?.text("followingStatus")?.ifBlank { null },
        )
        serverUserIdsByNickname[profile.nickname] = profile.userId
        serverProfiles = serverProfiles + (profile.nickname to profile)
        return profile
    }
    fun replaceServerComments(postLocalId: Long, rows: List<JsonObject>, mineId: String) {
        val index = feed.indexOfFirst { it.post.id == postLocalId }
        if (index < 0) return
        val comments = rows.map { row ->
            val id = row.text("commentId").hashCode().toLong()
            serverCommentIds[id] = row.text("commentId")
            val author = row.getAsJsonObject("author")
            FeedComment(id, author.text("nickname"), row.text("content"), author.text("userId") == mineId)
        }
        feed[index] = feed[index].copy(comments = comments)
    }
    fun serverCommentId(localId: Long): String? = serverCommentIds[localId]
    fun serverUserId(nickname: String): String? = serverUserIdsByNickname[nickname]
    fun rememberServerUsers(rows: List<JsonObject>) {
        rows.forEach { row ->
            val nickname = row.text("nickname")
            val userId = row.text("userId")
            if (nickname.isNotBlank() && userId.isNotBlank()) serverUserIdsByNickname[nickname] = userId
        }
    }
    fun replaceServerNotices(rows: List<JsonObject>) {
        accounts.notices.clear(); serverNoticeIds.clear()
        rows.forEach { row ->
            val id = row.text("notificationId").hashCode().toLong()
            serverNoticeIds[id] = row.text("notificationId")
            val type = row.text("type")
            accounts.notices += AppNotice(id, row.text("body").ifBlank { type }, when {
                type.startsWith("DM") -> "DM"; type.startsWith("POST") -> "커뮤니티"; type.startsWith("FOLLOW") -> "커뮤니티"; else -> "안내"
            }, if (type.startsWith("FOLLOW")) "FOLLOW" else row.text("resourceType"), row.text("resourceId").ifBlank { null }, row.text("readAt").isNotBlank())
        }
    }
    fun serverNoticeId(localId: Long): String? = serverNoticeIds[localId]
    fun replaceServerConversations(rows: List<JsonObject>) {
        chats.clear(); serverConversationIds.clear(); serverConversationOtherUsers.clear()
        rows.forEach { row ->
            val id = row.text("conversationId").hashCode().toLong()
            serverConversationIds[id] = row.text("conversationId")
            serverConversationOtherUsers[id] = row.text("otherUserId")
            chats += ChatRoom(id, "메이트 ${row.text("otherUserId").take(8)}", emptyList(),
                if (row.text("status") == "ACTIVE") "대화 가능" else if (row.text("status") == "REJECTED") "거절" else "요청 대기",
                incoming = row.text("requesterUserId") == row.text("otherUserId"))
        }
    }
    fun serverConversationId(localId: Long): String? = serverConversationIds[localId]
    fun serverChat(conversationId: String?): ChatRoom? = conversationId?.let { value -> chats.firstOrNull { serverConversationIds[it.id] == value } }
    fun serverChatForUser(userId: String): ChatRoom? = chats.firstOrNull { serverConversationOtherUsers[it.id] == userId }
    fun isMyServerMessage(localId: Long, senderId: String): Boolean = senderId != serverConversationOtherUsers[localId]
    val chats = mutableStateListOf<ChatRoom>().apply {
        addAll(conversations.map { chat -> ChatRoom(chat.id, chat.participantName, chat.messages.map { ChatEntry(nextId(), it.text, it.isMine) }).apply { unread = chat.unreadCount } })
        add(ChatRoom(nextId(), "요청 메이트", listOf(ChatEntry(nextId(), "같이 운동하고 싶어요!", false)), "요청 대기"))
    }
    var blocked by mutableStateOf(emptySet<String>())
    var mutual by mutableStateOf(emptySet<String>())
    var following by mutableStateOf(posts.filter { it.isFollowing }.map { it.authorName }.toSet())
    var notice by mutableStateOf<String?>(null)
    var selectedPostId by mutableStateOf<Long?>(null)
    fun chat(name: String): ChatRoom = chats.firstOrNull { it.name == name }?.apply { left = false }
        ?: ChatRoom(nextId(), name, emptyList(), incoming = false).also { chats.add(it) }
    fun send(room: ChatRoom) {
        if (room.name in blocked || room.status in listOf("거절", "차단") || (room.status == "요청 대기" && (room.incoming || room.requestSent))) return
        if (room.draft.isBlank() && room.photo == null) return
        // A first message request is text-only; images are enabled after acceptance.
        if (room.status == "요청 대기" && room.photo != null) return
        room.messages = room.messages + ChatEntry(nextId(), room.draft.trim(), true, room.photo, "전송 중", failOnce = room.photo != null && room.failNextImage)
        if (room.status == "요청 대기") room.requestSent = true
        room.draft = ""; room.photo = null; room.failNextImage = false
    }
}

@Composable internal fun SocialDelivery(state: SocialState) {
    val pending = state.chats.flatMap { it.messages }.filter { it.state == "전송 중" }.map { it.id }
    LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            delay(650)
            state.chats.forEach { room -> room.messages = room.messages.map { message ->
                if (message.id in pending) message.copy(state = if (message.failOnce) "전송 실패" else "전송 완료", failOnce = false) else message
            } }
        }
    }
}

@Composable internal fun CommunityFlow(modifier: Modifier, state: SocialState, nickname: String, workouts: List<WorkoutSummary>, runs: List<RunResult>, server: ServerSocialApi? = null, myUserId: String = "") {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var serverError by remember { mutableStateOf<String?>(null) }
    var feedCursor by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadingFeed by remember { mutableStateOf(server != null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(server, reload) {
        if (server == null) return@LaunchedEffect
        loadingFeed = true; serverError = null
        runCatching { withContext(Dispatchers.IO) { server.feedPage() to server.blocks() } }.onSuccess { (page, blocks) ->
            state.replaceServerFeed(page.rows("items"), myUserId)
            feedCursor = page.text("nextCursor").takeIf { page.get("hasNext")?.asBoolean == true && it.isNotBlank() }
            state.rememberServerUsers(blocks)
            state.blocked = blocks.map { it.text("nickname") }.toSet()
        }
            .onFailure { serverError = it.message ?: "커뮤니티를 불러오지 못했습니다." }
        loadingFeed = false
    }
    val targetPost = remember { state.feed.firstOrNull { it.post.id == state.selectedPostId } }
    var tab by state.communityTab
    LaunchedEffect(Unit) {
        if (targetPost != null) tab = if (targetPost.mine) "내 게시물" else if (targetPost.visibility == "맞팔 공개") "팔로잉" else "전체 공개"
        state.selectedPostId = null; state.openComposer = false
    }
    var composing by remember { mutableStateOf(state.openComposer) }
    var draft by state.composerText
    var photos by state.composerPhotos
    val selectedPhotos = photos ?: listOfNotNull(state.composerPhoto.value)
    var workout by state.composerWorkout
    var visibility by state.composerVisibility
    var editing by state.composerEditing
    var discard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<FeedEntry?>(null) }
    fun edit(entry: FeedEntry) {
        editing = entry.post.id; draft = entry.post.content; photos = entry.photoList()
        workout = entry.workout; visibility = entry.visibility; composing = true
    }
    if (discard) ConfirmAction("초안 삭제", "작성 중인 내용과 첨부를 삭제할까요?", { discard = false }) {
        state.clearComposer(); discard = false; composing = false
    }
    pendingEdit?.let { entry -> ConfirmAction("작성 중인 초안이 있어요", "초안을 버리고 이 게시물을 수정할까요?", { pendingEdit = null }) {
        state.clearComposer(); edit(entry); pendingEdit = null
    } }
    var deleting by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var limit by remember(tab) { mutableStateOf(10) }
    deleting?.let { id -> ConfirmAction("게시물 삭제", "이 게시물과 댓글을 삭제할까요?", { deleting = null }) {
        val serverId = state.serverPostId(id)
        if (server != null && serverId != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { server.deletePost(serverId); server.feed() } }
                .onSuccess { state.replaceServerFeed(it, myUserId); deleting = null }
                .onFailure { error = it.message ?: "게시물을 삭제하지 못했습니다."; deleting = null }
        } else { state.feed.removeAll { it.post.id == id && it.mine }; deleting = null }
    } }
    val screenLoading = loadingFeed
    Screen(modifier, background = MaterialTheme.colorScheme.background) {
        SocialScreenTitle("커뮤니티", action = {
            FilledTonalIconButton({ composing = true; error = null }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.AddBox, "게시물 작성")
            }
        })
        state.notice?.let { Text(it) }
        if (screenLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorText(serverError)
        if (serverError != null && !screenLoading) TextButton({ reload++ }) { Text("다시 불러오기") }
        SocialTabs(listOf("전체 공개", "팔로잉", "내 게시물"), tab, accent = true) { tab = it }
        if (composing) SocialPanelSheet(
            title = if (editing == null) "새 게시물" else "게시물 수정",
            dismiss = { if (!saving) composing = false }, enabled = !saving,
            footer = {
                ErrorText(error)
                SocialPrimaryButton(if (saving) "저장 중…" else "게시 저장", Modifier.fillMaxWidth(), enabled = !saving) {

                    if (draft.isBlank() && selectedPhotos.isEmpty()) error = "내용 또는 사진을 추가하세요."
                    else if (server != null && editing == null) {
                        saving = true
                        val content = draft.trim()
                        val mutualOnly = visibility == "맞팔 공개"
                        val attachments = selectedPhotos.toList()
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                val mediaIds = attachments.map { server.communityMediaId(context, it) }
                                server.createPost(content, mutualOnly, mediaIds)
                            } }
                                .onSuccess { state.replaceServerFeed(listOf(it), myUserId, append = true); composing = false; tab = "내 게시물"; state.clearComposer() }
                                .onFailure { error = it.message ?: "게시물을 저장하지 못했습니다." }
                            saving = false
                        }
                    }
                    else if (server != null && editing != null && state.serverPostId(editing!!) != null && state.serverPostVersion(editing!!) != null) {
                        val postId = state.serverPostId(editing!!)!!
                        val version = state.serverPostVersion(editing!!)!!
                        saving = true
                        val content = draft.trim()
                        val mutualOnly = visibility == "맞팔 공개"
                        val attachments = selectedPhotos.toList()
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                val mediaIds = attachments.map { server.communityMediaId(context, it) }
                                server.updatePost(postId, content, mutualOnly, version, mediaIds)
                            } }
                                .onSuccess { state.replaceServerFeed(listOf(it), myUserId, append = true); composing = false; tab = "내 게시물"; state.clearComposer() }
                                .onFailure { error = it.message ?: "게시물을 수정하지 못했습니다. 최신 내용을 다시 확인해 주세요." }
                            saving = false
                        }
                    } else if (server != null) {
                        error = "서버 게시물 정보를 찾을 수 없어요. 목록을 다시 불러온 뒤 수정해 주세요."
                    } else {
                        val old = state.feed.firstOrNull { it.post.id == editing && it.mine }
                        val base = old?.post?.copy(content = draft.trim()) ?: CommunityPost(state.nextId(), nickname, draft.trim(), true, 0, 0, "방금 전")
                        val entry = FeedEntry(base, true, visibility, selectedPhotos.firstOrNull(), workout, old?.comments.orEmpty(), selectedPhotos)
                        if (old != null) state.feed[state.feed.indexOf(old)] = entry else state.feed.add(0, entry)
                        composing = false; tab = "내 게시물"; state.clearComposer()
                    }

                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ composing = false }, enabled = !saving) { Text("나중에 쓰기") }
                    TextButton({ discard = true }, enabled = !saving, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("초안 삭제") }
                }
            }
        ) {
            OutlinedTextField(draft, { draft = it.take(2000) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), label = { Text("오늘의 운동이나 생각") }, minLines = 3,
                shape = RoundedCornerShape(12.dp), supportingText = { Text("${draft.length} / 2,000") })
            SocialVisibility(visibility, !saving) { visibility = it }
            if (!saving) MultiplePhotoAction(selectedPhotos.size, redesigned = true) { added -> photos = (selectedPhotos + added).distinct().take(10) }
            selectedPhotos.forEachIndexed { index, uri ->
                FitMatePhoto(uri, server)
                TextButton({ photos = selectedPhotos.filterIndexed { i, _ -> i != index } }, enabled = !saving) { Text("${index + 1}번 사진 제거") }
            }
            Text("운동 기록 첨부", fontWeight = FontWeight.Bold)
            if (workouts.isEmpty() && runs.isEmpty()) SocialEmpty("완료한 운동이 없어요.", "먼저 운동을 기록해 주세요.", Icons.Outlined.FitnessCenter)
            workouts.take(3).forEachIndexed { index, summary ->
                val text = "헬스 ${index + 1} · ${summary.durationMinutes}분 · ${summary.sets.size}세트 · ${"%.0f".format(summary.totalVolumeKg)} kg"
                FilterChip(workout == text, { workout = text }, { Text(text) }, enabled = !saving)
            }
            runs.take(3).forEachIndexed { index, result ->
                val text = "러닝 ${index + 1} · ${"%.2f".format(result.meters / 1000.0)} km · ${result.seconds / 60}분"
                FilterChip(workout == text, { workout = text }, { Text(text) }, enabled = !saving)
            }
            workout?.let { Text("선택: $it"); TextButton({ workout = null }, enabled = !saving) { Text("운동 첨부 제거") } }
            Text("운동 요약만 공유하며 심박수·정확한 경로는 첨부하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
        val visible = state.feed.filter { entry ->
            entry.post.authorName !in state.blocked &&
                (entry.mine || entry.visibility == "전체 공개" || entry.post.authorName in state.mutual) &&
                when (tab) { "내 게시물" -> entry.mine; "팔로잉" -> entry.mine || entry.post.authorName in state.following; else -> entry.visibility == "전체 공개" }
        }
        if (visible.isEmpty() && !screenLoading && serverError == null) SocialEmpty("표시할 게시물이 없어요.", "운동 이야기로 첫 게시물을 남겨보세요.")
        visible.take(limit).forEach { entry -> key(entry.post.id) {
            var commentsOpen by remember { mutableStateOf(entry.post.id == targetPost?.post?.id) }
            var comment by remember { mutableStateOf("") }
            fun update(transform: (FeedEntry) -> FeedEntry) {
                val index = state.feed.indexOfFirst { it.post.id == entry.post.id }
                if (index >= 0) state.feed[index] = transform(state.feed[index])
            }
            val menu = if (entry.mine) linkedMapOf<String, () -> Unit>(
                "수정" to {
                    if (editing == entry.post.id) composing = true
                    else if (draft.isNotBlank() || selectedPhotos.isNotEmpty() || workout != null) pendingEdit = entry
                    else edit(entry)
                },
                "삭제" to { deleting = entry.post.id }
            ) else linkedMapOf<String, () -> Unit>(
                "프로필 보기" to { state.accounts.opened = entry.post.authorName },
                "게시물 신고" to {
                    state.accounts.reportTarget = ReportTarget(
                        label = "게시물: ${entry.post.authorName}", targetType = "POST", targetId = state.serverPostId(entry.post.id),
                    )
                },
                (if (entry.post.authorName in state.following) "팔로우 해제" else "팔로우") to {
                    val userId = state.serverUserId(entry.post.authorName)
                    if (server != null && userId != null) scope.launch {
                        runCatching { withContext(Dispatchers.IO) { if (entry.post.authorName in state.following) server.unfollow(userId) else server.follow(userId) } }
                            .onSuccess { state.accounts.follow(state, entry.post.authorName) }
                            .onFailure { serverError = it.message ?: "팔로우를 처리하지 못했습니다." }
                    } else state.accounts.follow(state, entry.post.authorName)
                },
                "차단" to {
                    val userId = state.serverUserId(entry.post.authorName)
                    if (server != null && userId != null) scope.launch {
                        runCatching { withContext(Dispatchers.IO) { server.block(userId) } }
                            .onSuccess { state.blocked = state.blocked + entry.post.authorName }
                            .onFailure { serverError = it.message ?: "사용자를 차단하지 못했습니다." }
                    } else state.blocked = state.blocked + entry.post.authorName
                }
            )
            EditorialPost(entry,
                onLike = {
                    val serverId = state.serverPostId(entry.post.id)
                    if (server != null && serverId != null) scope.launch {
                        runCatching { withContext(Dispatchers.IO) { if (entry.post.isLiked) server.unlike(serverId) else server.like(serverId); server.feed() } }
                            .onSuccess { state.replaceServerFeed(it, myUserId) }.onFailure { serverError = it.message ?: "좋아요를 처리하지 못했습니다." }
                    } else update { it.copy(post = it.post.copy(isLiked = !it.post.isLiked, likeCount = it.post.likeCount + if (it.post.isLiked) -1 else 1)) }
                },
                onComments = {
                    commentsOpen = true
                    val serverId = state.serverPostId(entry.post.id)
                    if (server != null && serverId != null) scope.launch {
                        runCatching { withContext(Dispatchers.IO) { server.comments(serverId) } }
                            .onSuccess { state.replaceServerComments(entry.post.id, it, myUserId) }
                            .onFailure { serverError = it.message ?: "댓글을 불러오지 못했습니다." }
                    }
                }, menu = menu, onAuthor = { if (!entry.mine) state.accounts.opened = entry.post.authorName }, server = server, redesigned = true)
            if (commentsOpen) SocialPanelSheet("댓글", { commentsOpen = false }, footer = {
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                ErrorText(serverError)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(comment, { comment = it.take(500) }, label = { Text("댓글 남기기") }, modifier = Modifier.weight(1f), maxLines = 3, shape = RoundedCornerShape(12.dp), supportingText = { Text("${comment.length} / 500") })
                    SocialPrimaryButton("게시", enabled = comment.isNotBlank()) {
                        val serverId = state.serverPostId(entry.post.id)
                        if (server != null && serverId != null) scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                server.addComment(serverId, comment.trim())
                                server.feed() to server.comments(serverId)
                            } }.onSuccess { (posts, comments) ->
                                state.replaceServerFeed(posts, myUserId)
                                state.replaceServerComments(entry.post.id, comments, myUserId)
                                comment = ""
                            }.onFailure { serverError = it.message ?: "댓글을 저장하지 못했습니다." }
                        } else { update { it.copy(comments = it.comments + FeedComment(state.nextId(), nickname, comment.trim(), true)) }; comment = "" }
                    }
                }
            }) {
                if (entry.comments.isEmpty()) Text("첫 댓글을 남겨보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.comments.forEach { reply -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialAccentAvatar(reply.author, 36)
                    Column(Modifier.weight(1f)) {
                        Text(reply.author, style = MaterialTheme.typography.labelLarge)
                        Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton({
                        val serverPostId = state.serverPostId(entry.post.id)
                        val serverCommentId = state.serverCommentId(reply.id)
                        if (reply.mine && server != null && serverPostId != null && serverCommentId != null) scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                server.deleteComment(serverPostId, serverCommentId)
                                server.feed() to server.comments(serverPostId)
                            } }.onSuccess { (posts, comments) ->
                                state.replaceServerFeed(posts, myUserId)
                                state.replaceServerComments(entry.post.id, comments, myUserId)
                            }
                                .onFailure { serverError = it.message ?: "댓글을 삭제하지 못했습니다." }
                        } else if (reply.mine) update { it.copy(comments = it.comments.filterNot { c -> c.id == reply.id }) }
                        else state.notice = "댓글 신고를 임시 접수했어요. 서버 연결 후 전송됩니다."
                    }) { Text(if (reply.mine) "삭제" else "신고") }
                } }
            }
        } }
        if (visible.size > limit) OutlinedButton({ limit += 10 }) { Text("게시물 더 보기") }
        var privacyTest by remember { mutableStateOf(false) }
        DebugOnly { TextButton({ privacyTest = !privacyTest }) { Text("개발 및 테스트 도구") } }
        if (privacyTest && com.fitmate.app.BuildConfig.DEBUG) SocialSheet({ privacyTest = false }) {
            val name = "테스트 비공개 메이트"
            Text("맞팔 공개 글은 팔로잉 탭에서 확인하세요.")
            OutlinedButton({
                if (state.feed.none { it.post.authorName == name }) state.feed.add(0, FeedEntry(CommunityPost(state.nextId(), name, "맞팔에게만 보이는 운동 일지", true, 0, 0, "방금 전"), visibility = "맞팔 공개"))
                tab = "팔로잉"
            }) { Text("맞팔 공개 예시 글 준비") }
            ToggleLine("테스트 메이트와 맞팔", name in state.mutual) {
                state.mutual = if (it) state.mutual + name else state.mutual - name
                state.following = if (it) state.following + name else state.following - name
            }
        }
        feedCursor?.takeIf { visible.size <= limit }?.let { cursor ->
            OutlinedButton({
                if (server != null && !loadingMore) {
                    loadingMore = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { server.feedPage(cursor = cursor) } }
                            .onSuccess { page ->
                                state.replaceServerFeed(page.rows("items"), myUserId, append = true)
                                feedCursor = page.text("nextCursor").takeIf { page.get("hasNext")?.asBoolean == true && it.isNotBlank() }
                                limit += 20
                            }
                            .onFailure { serverError = it.message ?: "게시물을 더 불러오지 못했습니다." }
                        loadingMore = false
                    }
                }
            }, enabled = !loadingMore) { Text(if (loadingMore) "불러오는 중…" else "게시물 더 보기") }
        }
        if (state.blocked.isNotEmpty()) SoftCard {
            Text("차단 관리")
            state.blocked.forEach { name -> TextButton({
                val userId = state.serverUserId(name)
                if (server != null && userId != null) scope.launch {
                    runCatching { withContext(Dispatchers.IO) { server.unblock(userId) } }
                        .onSuccess { state.blocked = state.blocked - name }
                        .onFailure { serverError = it.message ?: "차단을 해제하지 못했습니다." }
                } else state.blocked = state.blocked - name
            }) { Text("$name 차단 해제") } }
        }
    }
}

@Composable internal fun DmListFlow(modifier: Modifier, state: SocialState, open: (ChatRoom) -> Unit, server: ServerSocialApi? = null) {
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(server != null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(server, reload) {
        if (server != null) {
            loading = true; error = null
            runCatching { withContext(Dispatchers.IO) { server.conversations() } }
                .onSuccess(state::replaceServerConversations)
                .onFailure { error = it.message ?: "대화 목록을 불러오지 못했습니다." }
            loading = false
        }
    }
    val screenLoading = loading
    Screen(modifier, background = MaterialTheme.colorScheme.background) {
    SocialScreenTitle("DM", "요청을 수락하고 메이트와 대화하세요.")
    if (screenLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    ErrorText(error)
    if (error != null) TextButton({ reload++ }) { Text("다시 불러오기") }
    val requests = state.chats.filter { !it.left && it.status == "요청 대기" && it.name !in state.blocked }
    Text("메시지 요청 ${requests.size}개", fontWeight = FontWeight.Bold)
    if (requests.isEmpty() && !screenLoading && error == null) SocialEmpty("새로운 메시지 요청이 없습니다.", "새 요청이 도착하면 여기에서 확인하세요.")
    requests.forEach { room -> SocialRoomCard(room, request = true) { room.unread = 0; open(room) } }
    Text("대화", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val active = state.chats.filter { !it.left && it.status == "대화 가능" && it.name !in state.blocked }
    if (active.isEmpty() && !screenLoading && error == null) SocialEmpty("아직 대화가 없습니다.", "메이트와 첫 대화를 시작해 보세요.")
    active.forEach { room -> SocialRoomCard(room, request = false) { room.unread = 0; open(room) } }
    var toolsOpen by remember { mutableStateOf(false) }
    DebugOnly { TextButton({ toolsOpen = true }) { Text("개발 및 테스트 도구") } }
    if (toolsOpen && com.fitmate.app.BuildConfig.DEBUG) SocialSheet({ toolsOpen = false }) {
        Text("테스트: 요청 상태 확인")
        OutlinedButton({
            val id = state.nextId()
            val room = ChatRoom(id, "비공개 메이트 $id", emptyList(), "요청 대기", incoming = false)
            state.chats.add(room); open(room)
        }) { Text("일방 팔로워로 요청 보내기") }
        state.chats.filter { it.status == "거절" || it.name in state.blocked }.forEach { room ->
            Text("${room.name} · ${if (room.name in state.blocked) "차단됨" else "요청 거절됨"}")
            if (room.name in state.blocked) TextButton({ state.blocked = state.blocked - room.name }) { Text("차단 해제") }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun DmChatFlow(modifier: Modifier, state: SocialState, room: ChatRoom, back: () -> Unit, server: ServerSocialApi? = null, navigateUp: () -> Unit = back) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val serverConversationId = state.serverConversationId(room.id)
    val scroll = rememberLazyListState()
    var limit by remember(room.id) { mutableStateOf(30) }
    var action by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(serverConversationId != null && server != null) }
    var reload by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(serverConversationId, reload) {
        if (server != null && serverConversationId != null) {
            loading = true; loadFailed = false; notice = null
            runCatching { withContext(Dispatchers.IO) { server.messages(serverConversationId) } }.onSuccess { rows ->
                room.messages = rows.map { row -> ChatEntry(row.text("messageId").hashCode().toLong(), row.text("content"), state.isMyServerMessage(room.id, row.text("senderUserId")), photo = row.text("mediaUrl").ifBlank { null }, time = row.text("createdAt").takeLast(8).take(5), read = row.text("readAt").isNotBlank()) }
            }.onFailure { loadFailed = true; notice = it.message ?: "메시지를 불러오지 못했습니다." }
            loading = false
        }
    }
    var options by remember { mutableStateOf(false) }
    var testTools by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<ChatEntry?>(null) }
    val blocked = room.name in state.blocked
    val canSend = !blocked && (room.status == "대화 가능" || (room.status == "요청 대기" && !room.incoming && !room.requestSent))
    val accepted = room.status == "대화 가능"
    LaunchedEffect(room.messages.size) {
        room.unread = 0
        if (room.messages.isNotEmpty()) scroll.animateScrollToItem(room.messages.takeLast(limit).size)
    }
    action?.let { selected -> ConfirmAction(selected, "계속 진행할까요?", { action = null }) {
        when (selected) {
            "차단" -> state.blocked = state.blocked + room.name
            "대화방 나가기" -> { room.left = true; room.unread = 0; back() }
        }
        action = null
    } }
    deleting?.let { id -> ConfirmAction("내 메시지 삭제", "보낸 메시지를 삭제할까요?", { deleting = null }) {
        room.messages = room.messages.filterNot { it.id == id && it.mine }; deleting = null
    } }
    selectedMessage?.let { message -> SocialSheet({ selectedMessage = null }) {
        Text("메시지 옵션", style = MaterialTheme.typography.titleMedium)
        TextButton({
            if (message.mine) deleting = message.id else notice = "신고를 임시 접수했어요. 서버 연결 후 전송됩니다."
            selectedMessage = null
        }) { Text(if (message.mine) "삭제" else "신고") }
    } }
    if (attachments) SocialSheet({ attachments = false }) {
        Text("사진 첨부", style = MaterialTheme.typography.titleMedium)
        PhotoActions({ room.photo = it; attachments = false })
    }
    if (options) SocialSheet({ options = false }) {
        Text("대화 옵션", style = MaterialTheme.typography.titleMedium)
        TextButton({ room.muted = !room.muted }) { Text(if (room.muted) "알림 켜기" else "알림 끄기") }
        TextButton({ if (blocked) state.blocked = state.blocked - room.name else action = "차단"; options = false }) { Text(if (blocked) "차단 해제" else "차단") }
        TextButton({ action = "대화방 나가기"; options = false }) { Text("대화방 나가기") }
        DebugOnly { TextButton({ testTools = !testTools }) { Text("개발 및 테스트 도구") } }
        if (testTools && com.fitmate.app.BuildConfig.DEBUG && accepted && !blocked) {
            TextButton({ room.messages = room.messages.map { if (it.mine && it.state == "전송 완료") it.copy(read = true) else it } }) { Text("상대 읽음") }
            TextButton({ room.messages = room.messages + ChatEntry(state.nextId(), "확인했어요!", false) }) { Text("답장 받기") }
            ToggleLine("다음 이미지 전송 실패 재현", room.failNextImage) { room.failNextImage = it }
        }
    }
    Column(modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(navigateUp) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") }
            SocialAccentAvatar(room.name, 40)
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (room.muted) Text("알림 끔", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ options = true }) { Icon(Icons.Outlined.MoreHoriz, "대화 옵션") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), state = scroll, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column {
                    if (blocked) WorkoutCard { Text("차단 상태에서는 메시지와 요청을 보낼 수 없어요.") }
                    if (room.status == "거절") WorkoutCard { Text("거절된 요청입니다. 추가 메시지를 보낼 수 없어요.") }
                    if (!blocked && room.status == "요청 대기") WorkoutCard {
                        Text(if (room.incoming) "받은 메시지 요청" else "보낸 메시지 요청", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (room.incoming) "수락하면 메이트와 대화할 수 있어요." else if (room.requestSent) "요청을 보냈어요. 상대방의 수락을 기다려 주세요." else "최초 요청 메시지는 1개만 보낼 수 있어요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        if (room.incoming) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SocialPrimaryButton("수락", Modifier.weight(1f)) {
                                if (server != null && serverConversationId != null) scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { server.respondToMessageRequest(serverConversationId, true) } }
                                        .onSuccess { room.status = "대화 가능" }.onFailure { notice = it.message ?: "요청을 수락하지 못했습니다." }
                                } else { state.following = state.following + room.name; state.accounts.followers = state.accounts.followers + room.name; state.accounts.reconcile(state); room.status = "대화 가능" }
                            }
                            TextButton({
                                if (server != null && serverConversationId != null) scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { server.respondToMessageRequest(serverConversationId, false) } }
                                        .onSuccess { room.status = "거절" }.onFailure { notice = it.message ?: "요청을 거절하지 못했습니다." }
                                } else room.status = "거절"
                            }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("거절") }
                        } else if (room.messages.any { it.mine && it.state == "전송 완료" }) {
                            Text("테스트: 상대방 응답")
                            Row {
                                TextButton({ state.following = state.following + room.name; state.accounts.followers = state.accounts.followers + room.name; state.accounts.reconcile(state); room.status = "대화 가능" }) { Text("수락 받기") }
                                TextButton({ room.status = "거절" }) { Text("거절 받기") }
                            }
                        }
                    }
                    if (room.messages.size > limit) TextButton({ limit += 30 }) { Text("이전 메시지 더 보기") }
                }
            }
            items(room.messages.takeLast(limit), key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Card(Modifier.combinedClickable(onClickLabel = "메시지 옵션", onClick = { selectedMessage = message }, onLongClickLabel = "메시지 옵션", onLongClick = { selectedMessage = message }),
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (message.mine) 4.dp else 18.dp, bottomStart = if (message.mine) 18.dp else 4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (message.mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                message.photo?.let { FitMatePhoto(it, server, Modifier.width(220.dp)) }
                                if (message.text.isNotEmpty()) Text(message.text)
                            }
                        }
                        Text("${message.time}${if (message.mine) " · ${message.state} · ${if (message.read) "읽음" else "1"}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            if (message.mine && message.state == "전송 실패") TextButton({ room.messages = room.messages.map { if (it.id == message.id) it.copy(state = "전송 중") else it } }, enabled = !blocked && (accepted || (room.status == "요청 대기" && !room.incoming && room.requestSent && message.photo == null))) { Text("재전송") }
                        }
                    }
                }
            }
            item {
                if (room.messages.isEmpty() && !loading && !loadFailed && canSend) Text("첫 메시지를 보내보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        notice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (loadFailed) TextButton({ reload++ }) { Text("다시 불러오기") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (canSend) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
            room.photo?.let { Text("사진 1장 첨부됨"); TextButton({ room.photo = null }) { Text("첨부 취소") } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accepted) FilledTonalIconButton({ attachments = true }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.Add, "사진 첨부") }
                OutlinedTextField(room.draft, { room.draft = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("메시지 입력") }, maxLines = 3, shape = RoundedCornerShape(14.dp))
                SocialPrimaryButton("전송", enabled = room.draft.isNotBlank() || room.photo != null) {
                    if (server != null && serverConversationId != null && accepted && (room.draft.isNotBlank() || room.photo != null)) scope.launch {
                        runCatching { withContext(Dispatchers.IO) {
                            val mediaId = room.photo?.let { server.uploadImage(context, it, "DM") }
                            server.sendMessage(serverConversationId, room.draft, mediaId); server.messages(serverConversationId)
                        } }.onSuccess { rows ->
                            room.messages = rows.map { row -> ChatEntry(row.text("messageId").hashCode().toLong(), row.text("content"), state.isMyServerMessage(room.id, row.text("senderUserId")), photo = row.text("mediaUrl").ifBlank { null }, time = row.text("createdAt").takeLast(8).take(5), read = row.text("readAt").isNotBlank()) }; room.draft = ""; room.photo = null
                        }.onFailure { notice = it.message ?: "메시지를 보내지 못했습니다." }
                    } else state.send(room)
                }
            }
        }
    }
}

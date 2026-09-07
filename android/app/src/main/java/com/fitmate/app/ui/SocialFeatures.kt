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
    val comments: List<FeedComment> = emptyList()
)
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
    val composerWorkout = mutableStateOf<String?>(null)
    val composerVisibility = mutableStateOf("전체 공개")
    val composerEditing = mutableStateOf<Long?>(null)
    var openComposer by mutableStateOf(false)
    fun clearComposer() {
        composerText.value = ""; composerPhoto.value = null; composerWorkout.value = null
        composerVisibility.value = "전체 공개"; composerEditing.value = null
    }
    private var serial = 10000L
    fun nextId() = ++serial
    internal fun restoreSerial(value: Long) { serial = maxOf(serial, value) }
    internal fun currentSerial() = serial
    val feed = mutableStateListOf<FeedEntry>().apply {
        addAll(posts.map { item -> FeedEntry(item, comments = List(item.commentCount) { i -> FeedComment(nextId(), "메이트 ${i + 1}", "오늘도 응원해요!", false) }) })
    }
    fun replaceServerFeed(rows: List<JsonObject>, mineId: String) {
        feed.clear(); serverUserIdsByNickname.clear()
        serverPostIds.clear(); serverPostVersions.clear(); serverCommentIds.clear()
        rows.forEach { row ->
            val id = row.text("postId").hashCode().toLong()
            serverPostIds[id] = row.text("postId")
            serverPostVersions[id] = row.get("version")?.asLong ?: 0L
            val author = row.getAsJsonObject("author")
            val name = author.text("nickname")
            serverUserIdsByNickname[name] = author.text("userId")
            feed += FeedEntry(
                CommunityPost(id, name, row.text("content"), row.get("likedByMe")?.asBoolean ?: false,
                    row.get("likeCount")?.asInt ?: 0, row.get("commentCount")?.asInt ?: 0, row.text("createdAt")),
                mine = author.text("userId") == mineId,
                visibility = if (row.text("visibility") == "MUTUAL") "맞팔 공개" else "전체 공개",
                photo = row.getAsJsonArray("mediaUrls")?.firstOrNull()?.asString,
            )
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
    LaunchedEffect(server) {
        if (server == null) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { server.feed() to server.blocks() } }.onSuccess { (posts, blocks) ->
            state.replaceServerFeed(posts, myUserId)
            state.rememberServerUsers(blocks)
            state.blocked = blocks.map { it.text("nickname") }.toSet()
        }
            .onFailure { serverError = it.message ?: "커뮤니티를 불러오지 못했습니다." }
    }
    val targetPost = remember { state.feed.firstOrNull { it.post.id == state.selectedPostId } }
    var tab by state.communityTab
    LaunchedEffect(Unit) {
        if (targetPost != null) tab = if (targetPost.mine) "내 게시물" else if (targetPost.visibility == "맞팔 공개") "팔로잉" else "전체 공개"
        state.selectedPostId = null; state.openComposer = false
    }
    var composing by remember { mutableStateOf(state.openComposer) }
    var draft by state.composerText
    var photo by state.composerPhoto
    var workout by state.composerWorkout
    var visibility by state.composerVisibility
    var editing by state.composerEditing
    var discard by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<FeedEntry?>(null) }
    fun edit(entry: FeedEntry) {
        editing = entry.post.id; draft = entry.post.content; photo = entry.photo
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
    Screen(modifier, background = FeedBackground) {
        SocialTitle("커뮤니티", Icons.Outlined.AddBox, "게시물 작성") {
            composing = true; error = null
        }
        state.notice?.let { Text(it) }
        ErrorText(serverError)
        SocialTabs(listOf("전체 공개", "팔로잉", "내 게시물"), tab) { tab = it }
        if (composing) SocialSheet({ composing = false }) {
            Text(if (editing == null) "새 게시물" else "게시물 수정", fontWeight = FontWeight.Bold)
            OutlinedTextField(draft, { draft = it.take(2000) }, modifier = Modifier.fillMaxWidth(), label = { Text("오늘의 운동이나 생각") }, minLines = 3)
            Choices(listOf("전체 공개", "맞팔 공개"), visibility) { visibility = it }
            PhotoActions({ photo = it })
            photo?.let { uri -> LocalPhoto(uri); TextButton({ photo = null }) { Text("이미지 제거") } }
            Text("운동 기록 첨부", fontWeight = FontWeight.Bold)
            if (workouts.isEmpty() && runs.isEmpty()) Text("완료한 운동이 없어요. 먼저 운동을 기록해 주세요.")
            workouts.take(3).forEachIndexed { index, summary ->
                val text = "헬스 ${index + 1} · ${summary.durationMinutes}분 · ${summary.sets.size}세트 · ${"%.0f".format(summary.totalVolumeKg)} kg"
                FilterChip(workout == text, { workout = text }, { Text(text) })
            }
            runs.take(3).forEachIndexed { index, result ->
                val text = "러닝 ${index + 1} · ${"%.2f".format(result.meters / 1000.0)} km · ${result.seconds / 60}분"
                FilterChip(workout == text, { workout = text }, { Text(text) })
            }
            workout?.let { Text("선택: $it"); TextButton({ workout = null }) { Text("운동 첨부 제거") } }
            Text("운동 요약만 공유하며 심박수·정확한 경로는 첨부하지 않습니다.", style = MaterialTheme.typography.bodySmall)
            ErrorText(error)
            Row {
                TextButton({ composing = false }) { Text("나중에 쓰기") }
                TextButton({ discard = true }) { Text("초안 삭제") }
                Button({
                    if (draft.isBlank()) error = "게시물 내용을 입력하세요."
                    else if (server != null && editing == null) {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                val mediaIds = photo?.let { listOf(server.uploadImage(context, it, "COMMUNITY_POST")) }.orEmpty()
                                server.createPost(draft.trim(), visibility == "맞팔 공개", mediaIds); server.feed()
                            } }
                                .onSuccess { state.replaceServerFeed(it, myUserId); composing = false; tab = "내 게시물"; state.clearComposer() }
                                .onFailure { error = it.message ?: "게시물을 저장하지 못했습니다." }
                        }
                    }
                    else if (server != null && editing != null && state.serverPostId(editing!!) != null && state.serverPostVersion(editing!!) != null) {
                        val postId = state.serverPostId(editing!!)!!
                        val version = state.serverPostVersion(editing!!)!!
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                val mediaIds = photo?.let { listOf(server.uploadImage(context, it, "COMMUNITY_POST")) }
                                server.updatePost(postId, draft.trim(), visibility == "맞팔 공개", version, mediaIds); server.feed()
                            } }
                                .onSuccess { state.replaceServerFeed(it, myUserId); composing = false; tab = "내 게시물"; state.clearComposer() }
                                .onFailure { error = it.message ?: "게시물을 수정하지 못했습니다. 최신 내용을 다시 확인해 주세요." }
                        }
                    } else {
                        val old = state.feed.firstOrNull { it.post.id == editing && it.mine }
                        val base = old?.post?.copy(content = draft.trim()) ?: CommunityPost(state.nextId(), nickname, draft.trim(), true, 0, 0, "방금 전")
                        val entry = FeedEntry(base, true, visibility, photo, workout, old?.comments.orEmpty())
                        if (old != null) state.feed[state.feed.indexOf(old)] = entry else state.feed.add(0, entry)
                        composing = false; tab = "내 게시물"; state.clearComposer()
                    }
                }) { Text("게시 저장") }
            }
        }
        val visible = state.feed.filter { entry ->
            entry.post.authorName !in state.blocked &&
                (entry.mine || entry.visibility == "전체 공개" || entry.post.authorName in state.mutual) &&
                when (tab) { "내 게시물" -> entry.mine; "팔로잉" -> entry.mine || entry.post.authorName in state.following; else -> entry.visibility == "전체 공개" }
        }
        if (visible.isEmpty()) SoftCard { Text("표시할 게시물이 없어요.") }
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
                    else if (draft.isNotBlank() || photo != null || workout != null) pendingEdit = entry
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
                }, menu = menu, onAuthor = { if (!entry.mine) state.accounts.opened = entry.post.authorName }, server = server)
            if (commentsOpen) SocialSheet({ commentsOpen = false }) {
                Text("댓글", style = MaterialTheme.typography.titleLarge)
                if (entry.comments.isEmpty()) Text("첫 댓글을 남겨보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.comments.forEach { reply -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialAvatar(reply.author, 32)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(comment, { comment = it.take(500) }, label = { Text("댓글 남기기") }, modifier = Modifier.weight(1f), maxLines = 3)
                    TextButton({
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
                    }, enabled = comment.isNotBlank()) { Text("게시") }
                }
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

@Composable internal fun DmListFlow(modifier: Modifier, state: SocialState, open: (ChatRoom) -> Unit, server: ServerSocialApi? = null) = Screen(modifier) {
    LaunchedEffect(server) { if (server != null) runCatching { withContext(Dispatchers.IO) { server.conversations() } }.onSuccess(state::replaceServerConversations) }
    SectionTitle("DM", "요청을 수락하고 메이트와 대화하세요.")
    val requests = state.chats.filter { !it.left && it.status == "요청 대기" && it.name !in state.blocked }
    Text("메시지 요청 ${requests.size}개", fontWeight = FontWeight.Bold)
    if (requests.isEmpty()) Text("새로운 메시지 요청이 없습니다.")
    requests.forEach { room -> SoftCard {
        Text("${room.name} · ${if (room.incoming) "받은 요청" else "보낸 요청"}")
        Text(room.messages.lastOrNull()?.text ?: "첫 메시지를 작성해 주세요.")
        OutlinedButton({ room.unread = 0; open(room) }) { Text("요청 보기") }
    } }
    Text("대화", fontWeight = FontWeight.Bold)
    val active = state.chats.filter { !it.left && it.status == "대화 가능" && it.name !in state.blocked }
    if (active.isEmpty()) Text("아직 대화가 없습니다.")
    active.forEach { room ->
        Row(Modifier.fillMaxWidth().clickable { room.unread = 0; open(room) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialAvatar(room.name, 48)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${room.name}${if (room.muted) " · 알림 끔" else ""}", fontWeight = FontWeight.Bold)
                Text(room.messages.lastOrNull()?.let { if (it.photo != null) "사진" else it.text } ?: "첫 메시지를 보내보세요.", maxLines = 1)
            }
            if (room.unread > 0) Text("${room.unread}", color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun DmChatFlow(modifier: Modifier, state: SocialState, room: ChatRoom, back: () -> Unit, server: ServerSocialApi? = null) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val serverConversationId = state.serverConversationId(room.id)
    LaunchedEffect(serverConversationId) {
        if (server != null && serverConversationId != null) runCatching { withContext(Dispatchers.IO) { server.messages(serverConversationId) } }.onSuccess { rows ->
            room.messages = rows.map { row -> ChatEntry(row.text("messageId").hashCode().toLong(), row.text("content"), state.isMyServerMessage(room.id, row.text("senderUserId")), photo = row.text("mediaUrl").ifBlank { null }, time = row.text("createdAt").takeLast(8).take(5), read = row.text("readAt").isNotBlank()) }
        }
    }
    val scroll = rememberLazyListState()
    var limit by remember(room.id) { mutableStateOf(30) }
    var action by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
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
    Column(modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton({ options = true }) { Icon(Icons.Outlined.MoreHoriz, "대화 옵션") }
        }
        LazyColumn(Modifier.weight(1f), state = scroll, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column {
                    if (blocked) Text("차단 상태에서는 메시지와 요청을 보낼 수 없어요.")
                    if (room.status == "거절") Text("거절된 요청입니다. 추가 메시지를 보낼 수 없어요.")
                    if (!blocked && room.status == "요청 대기") {
                        Text(if (room.incoming) "받은 메시지 요청" else "최초 요청 메시지는 1개만 보낼 수 있어요.")
                        if (room.incoming) Row {
                            Button({
                                if (server != null && serverConversationId != null) scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { server.respondToMessageRequest(serverConversationId, true) } }
                                        .onSuccess { room.status = "대화 가능" }.onFailure { notice = it.message ?: "요청을 수락하지 못했습니다." }
                                } else { state.following = state.following + room.name; state.accounts.followers = state.accounts.followers + room.name; state.accounts.reconcile(state); room.status = "대화 가능" }
                            }) { Text("수락") }
                            TextButton({
                                if (server != null && serverConversationId != null) scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { server.respondToMessageRequest(serverConversationId, false) } }
                                        .onSuccess { room.status = "거절" }.onFailure { notice = it.message ?: "요청을 거절하지 못했습니다." }
                                } else room.status = "거절"
                            }) { Text("거절") }
                        } else if (room.messages.any { it.mine && it.state == "전송 완료" }) {
                            Text("테스트: 상대방 응답")
                            Row {
                                TextButton({ state.following = state.following + room.name; state.accounts.followers = state.accounts.followers + room.name; state.accounts.reconcile(state); room.status = "대화 가능" }) { Text("수락 받기") }
                                TextButton({ room.status = "거절" }) { Text("거절 받기") }
                            }
                        }
                    }
                    notice?.let { Text(it) }
                    if (room.messages.size > limit) TextButton({ limit += 30 }) { Text("이전 메시지 더 보기") }
                }
            }
            items(room.messages.takeLast(limit), key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                    Column(horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                        Card(Modifier.widthIn(max = 280.dp).combinedClickable(onClickLabel = "메시지 옵션", onClick = { selectedMessage = message }, onLongClickLabel = "메시지 옵션", onLongClick = { selectedMessage = message }), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(10.dp)) {
                message.photo?.let { FitMatePhoto(it, server, Modifier.width(220.dp)) }
                                if (message.text.isNotEmpty()) Text(message.text)
                            }
                        }
                        Text("${message.time}${if (message.mine) " · ${message.state} · ${if (message.read) "읽음" else "1"}" else ""}", style = MaterialTheme.typography.labelSmall)
                        Row {
                            if (message.mine && message.state == "전송 실패") TextButton({ room.messages = room.messages.map { if (it.id == message.id) it.copy(state = "전송 중") else it } }, enabled = !blocked && (accepted || (room.status == "요청 대기" && !room.incoming && room.requestSent && message.photo == null))) { Text("재전송") }
                        }
                    }
                }
            }
            item {
                if (room.messages.isEmpty()) Text("첫 메시지를 보내보세요.")
            }
        }
        if (canSend) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(10.dp)) {
            room.photo?.let { Text("사진 1장 첨부됨"); TextButton({ room.photo = null }) { Text("첨부 취소") } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accepted) IconButton({ attachments = true }) { Icon(Icons.Outlined.Add, "사진 첨부") }
                OutlinedTextField(room.draft, { room.draft = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("메시지 입력") }, maxLines = 3)
                Button({
                    if (server != null && serverConversationId != null && accepted && (room.draft.isNotBlank() || room.photo != null)) scope.launch {
                        runCatching { withContext(Dispatchers.IO) {
                            val mediaId = room.photo?.let { server.uploadImage(context, it, "DM") }
                            server.sendMessage(serverConversationId, room.draft, mediaId); server.messages(serverConversationId)
                        } }.onSuccess { rows ->
                            room.messages = rows.map { row -> ChatEntry(row.text("messageId").hashCode().toLong(), row.text("content"), state.isMyServerMessage(room.id, row.text("senderUserId")), photo = row.text("mediaUrl").ifBlank { null }, time = row.text("createdAt").takeLast(8).take(5), read = row.text("readAt").isNotBlank()) }; room.draft = ""; room.photo = null
                        }.onFailure { notice = it.message ?: "메시지를 보내지 못했습니다." }
                    } else state.send(room)
                }, enabled = room.draft.isNotBlank() || room.photo != null) { Text("전송") }
            }
        }
    }
}

package com.fitmate.app.ui

import java.time.LocalDate

internal data class ChatSnapshot(
    val id: Long, val name: String, val messages: List<ChatEntry>, val status: String,
    val incoming: Boolean, val unread: Int, val muted: Boolean, val left: Boolean,
    val draft: String, val photo: String?, val requestSent: Boolean
) {
    fun restore() = ChatRoom(id, name, messages.map {
        if (it.state == "전송 중") it.copy(state = "전송 실패", failOnce = false) else it
    }, status, incoming).also {
        it.unread = unread; it.muted = muted; it.left = left; it.draft = draft
        it.photo = photo; it.requestSent = requestSent
    }
}

internal data class SocialSnapshot(
    val serial: Long, val feed: List<FeedEntry>, val chats: List<ChatSnapshot>,
    val blocked: Set<String>, val following: Set<String>, val followers: Set<String>,
    val incoming: Map<String, String>, val outgoing: Map<String, String>,
    val reports: List<ReportTicket>, val notices: List<AppNotice>,
    val push: Boolean, val preferences: Map<String, Boolean>,
    val draft: String, val photo: String?, val workout: String?, val visibility: String, val editing: Long?
) {
    fun restore(state: SocialState) {
        state.feed.clear(); state.feed.addAll(feed)
        state.chats.clear(); state.chats.addAll(chats.map { it.restore() })
        state.restoreSerial(serial)
        state.blocked = blocked; state.following = following
        state.accounts.apply {
            this.followers = this@SocialSnapshot.followers
            this.incoming = this@SocialSnapshot.incoming; this.outgoing = this@SocialSnapshot.outgoing
            reports.clear(); reports.addAll(this@SocialSnapshot.reports)
            notices.clear(); notices.addAll(this@SocialSnapshot.notices)
            push = this@SocialSnapshot.push; preferences = this@SocialSnapshot.preferences
            reconcile(state)
        }
        state.composerText.value = draft; state.composerPhoto.value = photo
        state.composerWorkout.value = workout; state.composerVisibility.value = visibility
        state.composerEditing.value = editing
    }
}

internal fun SocialState.snapshot() = SocialSnapshot(currentSerial(), feed.toList(), chats.map {
    ChatSnapshot(it.id, it.name, it.messages.toList(), it.status, it.incoming, it.unread, it.muted, it.left, it.draft, it.photo, it.requestSent)
}, blocked.toSet(), following.toSet(), accounts.followers.toSet(), accounts.incoming.toMap(), accounts.outgoing.toMap(),
    accounts.reports.toList(), accounts.notices.toList(), accounts.push, accounts.preferences.toMap(),
    composerText.value, composerPhoto.value, composerWorkout.value, composerVisibility.value, composerEditing.value)

// Dates are strings at the storage boundary; Gson must not reflect into java.time internals.
internal data class BodySnapshot(val id: String, val date: String, val weight: Double, val muscle: Double,
    val fat: Double, val extras: Map<String, Double>, val source: String) {
    fun restore() = BodyEntry(id, LocalDate.parse(date), weight, muscle, fat, extras, source)
}
internal fun BodyEntry.snapshot() = BodySnapshot(id, date.toString(), weight, muscle, fat, extras.toMap(), source)

internal data class MateRoomSnapshot(val id: Long, val status: String, val applicants: List<MateApplicant>,
    val completed: Boolean, val canceled: Boolean, val closed: Boolean, val evaluated: Set<String>,
    val evaluations: Map<String, MateEvaluation>, val revision: Int) {
    fun restore() = MateRoom().also {
        it.myStatus = status; it.applicants = applicants; it.completed = completed; it.canceled = canceled
        it.closed = closed; it.evaluated = evaluated; it.evaluations = evaluations; it.revision = revision
    }
}
internal data class MateSnapshot(val posts: List<com.fitmate.app.data.model.MatePost>, val rooms: List<MateRoomSnapshot>, val active: Long?) {
    fun restore(state: MateFeatureState) {
        state.rooms.clear(); rooms.forEach { state.rooms[it.id] = it.restore() }; state.activeWorkoutId = active
    }
}
internal fun MateFeatureState.snapshot(posts: List<com.fitmate.app.data.model.MatePost>) = MateSnapshot(posts.toList(), rooms.map { (id, room) ->
    MateRoomSnapshot(id, room.myStatus, room.applicants.toList(), room.completed, room.canceled, room.closed, room.evaluated.toSet(), room.evaluations.toMap(), room.revision)
}, activeWorkoutId)

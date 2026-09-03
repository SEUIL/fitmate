package com.fitmate.app.data.model

enum class Sport(val label: String) {
    FITNESS("헬스"), RUNNING("러닝"), BADMINTON("배드민턴"), CLIMBING("클라이밍"), HIKING("등산"), GOLF("골프")
}

data class UserProfile(
    val nickname: String,
    val region: String,
    val interests: Set<Sport>,
    val isProfileComplete: Boolean = false,
    val isPrivate: Boolean = false
)

data class MatePost(
    val id: Long,
    val title: String,
    val sport: Sport,
    val region: String,
    val dateTime: String,
    val duration: String,
    val capacity: Int,
    val participantCount: Int,
    val difficulty: String,
    val description: String,
    val authorName: String,
    val isBeginnerWelcome: Boolean
)

data class WorkoutSet(
    val exerciseName: String,
    val weightKg: Double,
    val repetitions: Int
)

data class WorkoutSummary(
    val durationMinutes: Int,
    val sets: List<WorkoutSet>,
    val usedSamsungHealth: Boolean
) {
    val totalVolumeKg: Double get() = sets.sumOf { it.weightKg * it.repetitions }
}

data class CommunityPost(
    val id: Long,
    val authorName: String,
    val content: String,
    val isFollowing: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val createdAt: String,
    val isLiked: Boolean = false
)

data class DirectMessage(
    val id: Long,
    val text: String,
    val isMine: Boolean
)

data class DmConversation(
    val id: Long,
    val participantName: String,
    val unreadCount: Int,
    val messages: List<DirectMessage>
) {
    val lastMessage: String get() = messages.lastOrNull()?.text.orEmpty()
}

data class InbodyRecord(
    val measuredAt: String,
    val weightKg: Double,
    val skeletalMuscleKg: Double,
    val bodyFatPercent: Double
)

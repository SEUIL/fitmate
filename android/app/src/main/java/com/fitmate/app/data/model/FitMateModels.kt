package com.fitmate.app.data.model

enum class Sport(val label: String) {
    FITNESS("헬스"), RUNNING("러닝"), BADMINTON("배드민턴"), CLIMBING("클라이밍"), HIKING("등산"), GOLF("골프")
}

data class UserProfile(
    val nickname: String,
    val region: String,
    val interests: Set<Sport>,
    val isProfileComplete: Boolean = false,
    val isPrivate: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val facilityName: String = "",
    val experience: String = "입문",
    val customInterests: Set<String> = emptySet(),
    val goals: Set<String> = emptySet(),
    val availableTime: String = "",
    val bio: String = "",
    val profileImageUrl: String? = null,
    val isAdultVerified: Boolean = false,
    val activityRegionId: String = ""
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
    val isBeginnerWelcome: Boolean,
    val details: MateDetails = MateDetails()
)

data class RecruitmentDraft(
    val title: String,
    val sport: Sport,
    val dateTime: String,
    val duration: String,
    val capacity: Int,
    val difficulty: String,
    val description: String,
    val isBeginnerWelcome: Boolean,
    val details: MateDetails = MateDetails()
)

data class MateDetails(
    val location: String = "",
    val meetingPoint: String = "",
    val cost: String = "0",
    val visibility: String = "전체 공개",
    val sportFields: Map<String, String> = emptyMap()
)

data class WorkoutSet(
    val exerciseName: String,
    val weightKg: Double,
    val repetitions: Int,
    val rpe: Int? = null,
    val memo: String = ""
)

data class WorkoutSummary(
    val durationMinutes: Int,
    val sets: List<WorkoutSet>,
    val usedSamsungHealth: Boolean,
    val targetSetCount: Int = 0,
    val completedAt: Long? = null
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

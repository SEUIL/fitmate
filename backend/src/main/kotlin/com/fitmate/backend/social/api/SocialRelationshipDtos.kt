package com.fitmate.backend.social.api

import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.social.domain.FollowStatus
import java.time.Instant
import java.util.UUID

data class RelationshipResponse(
    val followingStatus: FollowStatus?,
    val followedByStatus: FollowStatus?,
    val mutual: Boolean,
)

data class UserProfilePreviewResponse(
    val userId: UUID,
    val nickname: String,
    val displayName: String,
    val visibility: ProfileVisibility,
    val bio: String?,
    val profileImageUrl: String?,
    val followerCount: Long,
    val followingCount: Long,
    val canViewProfile: Boolean,
    val relationship: RelationshipResponse,
)

data class FollowResponse(
    val userId: UUID,
    val status: FollowStatus,
    val requestedAt: Instant,
    val relationship: RelationshipResponse,
)

data class FollowRequestResponse(
    val userId: UUID,
    val nickname: String,
    val displayName: String,
    val requestedAt: Instant,
)

data class SocialUserResponse(
    val userId: UUID,
    val nickname: String,
    val displayName: String,
    val relationship: RelationshipResponse,
)

data class BlockedUserResponse(
    val userId: UUID,
    val nickname: String,
    val displayName: String,
    val blockedAt: Instant,
)

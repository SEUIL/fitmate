package com.fitmate.backend.social.application

import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.member.domain.UserProfile
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.social.api.FollowRequestResponse
import com.fitmate.backend.social.api.FollowResponse
import com.fitmate.backend.social.api.RelationshipResponse
import com.fitmate.backend.social.api.SocialUserResponse
import com.fitmate.backend.social.api.UserProfilePreviewResponse
import com.fitmate.backend.social.domain.Follow
import com.fitmate.backend.social.domain.FollowId
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.repository.FollowRepository
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.notification.application.NotificationService
import com.fitmate.backend.notification.domain.NotificationType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class SocialRelationshipService(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
    private val follows: FollowRepository,
    private val blocks: BlockRepository,
    private val clock: Clock,
    private val notifications: NotificationService,
) {
    @Transactional(readOnly = true)
    fun profile(viewerId: UUID, targetId: UUID): UserProfilePreviewResponse {
        ensureNotBlocked(viewerId, targetId)
        val target = activeProfile(targetId)
        val relationship = relationship(viewerId, targetId)
        val canView = viewerId == targetId || target.visibility == ProfileVisibility.PUBLIC ||
            relationship.followingStatus == FollowStatus.ACCEPTED
        return UserProfilePreviewResponse(
            userId = targetId,
            nickname = target.nickname,
            displayName = target.displayName,
            visibility = target.visibility,
            bio = target.bio?.takeIf { canView },
            followerCount = follows.countByIdFollowingUserIdAndStatus(targetId, FollowStatus.ACCEPTED),
            followingCount = follows.countByIdFollowerUserIdAndStatus(targetId, FollowStatus.ACCEPTED),
            canViewProfile = canView,
            relationship = relationship,
        )
    }

    @Transactional
    fun follow(followerId: UUID, targetId: UUID): FollowResponse {
        requireDifferentUsers(followerId, targetId)
        ensureNotBlocked(followerId, targetId)
        val target = activeProfile(targetId)
        val now = clock.instant()
        val follow = follows.findForUpdate(followerId, targetId) ?: Follow(FollowId(followerId, targetId))
        when {
            follow.status == FollowStatus.ACCEPTED -> Unit
            target.visibility == ProfileVisibility.PUBLIC -> {
                follow.status = FollowStatus.ACCEPTED
                follow.respondedAt = now
            }
            else -> {
                follow.status = FollowStatus.PENDING
                follow.respondedAt = null
            }
        }
        follows.saveAndFlush(follow)
        if (follow.status == FollowStatus.PENDING) notifications.follow(targetId, followerId, NotificationType.FOLLOW_REQUEST, "새 팔로우 요청이 도착했습니다.")
        if (follow.status == FollowStatus.ACCEPTED) notifications.follow(targetId, followerId, NotificationType.FOLLOW_ACCEPTED, "새 팔로워가 생겼습니다.")
        return followResponse(followerId, targetId, follow)
    }

    @Transactional
    fun cancelOrUnfollow(followerId: UUID, targetId: UUID): FollowResponse {
        requireDifferentUsers(followerId, targetId)
        ensureNotBlocked(followerId, targetId)
        activeProfile(targetId)
        val follow = follows.findForUpdate(followerId, targetId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "팔로우 관계를 찾을 수 없습니다.")
        if (follow.status == FollowStatus.PENDING || follow.status == FollowStatus.ACCEPTED) {
            follow.status = FollowStatus.CANCELED
            follow.respondedAt = clock.instant()
            follows.saveAndFlush(follow)
        }
        return followResponse(followerId, targetId, follow)
    }

    @Transactional(readOnly = true)
    fun pendingRequests(userId: UUID): List<FollowRequestResponse> =
        follows.findAllByIdFollowingUserIdAndStatusOrderByUpdatedAtDesc(userId, FollowStatus.PENDING).mapNotNull { follow ->
            val followerId = requireNotNull(follow.id.followerUserId)
            profiles.findById(followerId).orElse(null)?.takeIf { it.deletedAt == null && !isBlockedEitherDirection(userId, followerId) }?.let { profile ->
                FollowRequestResponse(followerId, profile.nickname, profile.displayName, follow.createdAt)
            }
        }

    @Transactional(readOnly = true)
    fun followers(viewerId: UUID, targetId: UUID): List<SocialUserResponse> = relationshipUsers(viewerId, targetId, followers = true)

    @Transactional(readOnly = true)
    fun following(viewerId: UUID, targetId: UUID): List<SocialUserResponse> = relationshipUsers(viewerId, targetId, followers = false)

    @Transactional
    fun respond(targetId: UUID, followerId: UUID, accepted: Boolean): FollowResponse {
        requireDifferentUsers(targetId, followerId)
        ensureNotBlocked(targetId, followerId)
        activeProfile(followerId)
        val follow = follows.findForUpdate(followerId, targetId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "팔로우 요청을 찾을 수 없습니다.")
        if (follow.status != FollowStatus.PENDING) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        follow.status = if (accepted) FollowStatus.ACCEPTED else FollowStatus.REJECTED
        follow.respondedAt = clock.instant()
        follows.saveAndFlush(follow)
        if (accepted) notifications.follow(followerId, targetId, NotificationType.FOLLOW_ACCEPTED, "팔로우 요청이 수락되었습니다.")
        return followResponse(targetId, followerId, follow)
    }

    @Transactional
    fun removeFollower(targetId: UUID, followerId: UUID): FollowResponse {
        requireDifferentUsers(targetId, followerId)
        ensureNotBlocked(targetId, followerId)
        val follow = follows.findForUpdate(followerId, targetId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "팔로워를 찾을 수 없습니다.")
        if (follow.status != FollowStatus.ACCEPTED) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        follow.status = FollowStatus.CANCELED
        follow.respondedAt = clock.instant()
        follows.saveAndFlush(follow)
        return followResponse(targetId, followerId, follow)
    }

    private fun followResponse(viewerId: UUID, targetId: UUID, follow: Follow): FollowResponse =
        FollowResponse(targetId, follow.status, follow.createdAt, relationship(viewerId, targetId))

    private fun relationship(viewerId: UUID, targetId: UUID): RelationshipResponse {
        val following = follows.findByIdFollowerUserIdAndIdFollowingUserId(viewerId, targetId)?.status
        val followedBy = follows.findByIdFollowerUserIdAndIdFollowingUserId(targetId, viewerId)?.status
        return RelationshipResponse(following, followedBy, following == FollowStatus.ACCEPTED && followedBy == FollowStatus.ACCEPTED)
    }

    private fun relationshipUsers(viewerId: UUID, targetId: UUID, followers: Boolean): List<SocialUserResponse> {
        ensureNotBlocked(viewerId, targetId)
        val target = activeProfile(targetId)
        val relationship = relationship(viewerId, targetId)
        val canView = viewerId == targetId || target.visibility == ProfileVisibility.PUBLIC || relationship.followingStatus == FollowStatus.ACCEPTED
        if (!canView) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        val rows = if (followers) {
            follows.findAllByIdFollowingUserIdAndStatusOrderByUpdatedAtDesc(targetId, FollowStatus.ACCEPTED)
                .map { requireNotNull(it.id.followerUserId) }
        } else {
            follows.findAllByIdFollowerUserIdAndStatusOrderByUpdatedAtDesc(targetId, FollowStatus.ACCEPTED)
                .map { requireNotNull(it.id.followingUserId) }
        }
        return rows.mapNotNull { userId ->
            if (isBlockedEitherDirection(viewerId, userId)) return@mapNotNull null
            profiles.findById(userId).orElse(null)?.takeIf { it.deletedAt == null }?.let { profile ->
                SocialUserResponse(userId, profile.nickname, profile.displayName, relationship(viewerId, userId))
            }
        }
    }

    private fun activeProfile(userId: UUID): UserProfile {
        val user = users.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (user.accountStatus != AccountStatus.ACTIVE) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        return profiles.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
            .also { if (it.deletedAt != null) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
    }

    private fun requireDifferentUsers(first: UUID, second: UUID) {
        if (first == second) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "자기 자신을 팔로우할 수 없습니다.")
    }

    private fun ensureNotBlocked(firstUserId: UUID, secondUserId: UUID) {
        if (isBlockedEitherDirection(firstUserId, secondUserId)) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun isBlockedEitherDirection(firstUserId: UUID, secondUserId: UUID): Boolean =
        blocks.existsByIdBlockerUserIdAndIdBlockedUserId(firstUserId, secondUserId) ||
            blocks.existsByIdBlockerUserIdAndIdBlockedUserId(secondUserId, firstUserId)
}

package com.fitmate.backend.social.application

import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.social.api.BlockedUserResponse
import com.fitmate.backend.social.domain.Block
import com.fitmate.backend.social.domain.BlockId
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.social.repository.FollowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class SocialSafetyService(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
    private val blocks: BlockRepository,
    private val follows: FollowRepository,
    private val clock: Clock,
) {
    @Transactional
    fun block(blockerId: UUID, blockedId: UUID): BlockedUserResponse {
        requireDifferentUsers(blockerId, blockedId)
        activeProfile(blockedId)
        val block = blocks.findById(BlockId(blockerId, blockedId)).orElseGet { Block(BlockId(blockerId, blockedId)) }
        blocks.saveAndFlush(block)
        cancelFollow(blockerId, blockedId)
        cancelFollow(blockedId, blockerId)
        return blockedResponse(blockedId, block.createdAt)
    }

    @Transactional
    fun unblock(blockerId: UUID, blockedId: UUID) {
        requireDifferentUsers(blockerId, blockedId)
        val block = blocks.findById(BlockId(blockerId, blockedId))
            .orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "차단 관계를 찾을 수 없습니다.") }
        blocks.delete(block)
    }

    @Transactional(readOnly = true)
    fun blocks(blockerId: UUID): List<BlockedUserResponse> =
        blocks.findAllByIdBlockerUserIdOrderByCreatedAtDesc(blockerId).mapNotNull { block ->
            block.id.blockedUserId?.let { userId -> profiles.findById(userId).orElse(null)?.takeIf { it.deletedAt == null }?.let { profile ->
                BlockedUserResponse(userId, profile.nickname, profile.displayName, block.createdAt)
            } }
        }

    @Transactional(readOnly = true)
    fun isBlockedEitherDirection(firstUserId: UUID, secondUserId: UUID): Boolean =
        blocks.existsByIdBlockerUserIdAndIdBlockedUserId(firstUserId, secondUserId) ||
            blocks.existsByIdBlockerUserIdAndIdBlockedUserId(secondUserId, firstUserId)

    private fun cancelFollow(followerId: UUID, followingId: UUID) {
        val follow = follows.findForUpdate(followerId, followingId) ?: return
        if (follow.status == FollowStatus.CANCELED) return
        follow.status = FollowStatus.CANCELED
        follow.respondedAt = clock.instant()
        follows.saveAndFlush(follow)
    }

    private fun blockedResponse(userId: UUID, blockedAt: java.time.Instant): BlockedUserResponse {
        val profile = profiles.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        return BlockedUserResponse(userId, profile.nickname, profile.displayName, blockedAt)
    }

    private fun activeProfile(userId: UUID) {
        val user = users.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (user.accountStatus != AccountStatus.ACTIVE || profiles.findById(userId).orElse(null)?.deletedAt != null) {
            throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        }
    }

    private fun requireDifferentUsers(first: UUID, second: UUID) {
        if (first == second) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "자기 자신을 차단할 수 없습니다.")
    }
}

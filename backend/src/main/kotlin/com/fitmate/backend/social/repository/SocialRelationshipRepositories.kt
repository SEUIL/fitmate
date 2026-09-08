package com.fitmate.backend.social.repository

import com.fitmate.backend.social.domain.Follow
import com.fitmate.backend.social.domain.FollowId
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.domain.Block
import com.fitmate.backend.social.domain.BlockId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FollowRepository : JpaRepository<Follow, FollowId> {
    fun findByIdFollowerUserIdAndIdFollowingUserId(followerUserId: UUID, followingUserId: UUID): Follow?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select follow from Follow follow where follow.id.followerUserId = :followerUserId and follow.id.followingUserId = :followingUserId")
    fun findForUpdate(followerUserId: UUID, followingUserId: UUID): Follow?

    fun existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(followerUserId: UUID, followingUserId: UUID, status: FollowStatus): Boolean
    fun countByIdFollowingUserIdAndStatus(followingUserId: UUID, status: FollowStatus): Long
    fun countByIdFollowerUserIdAndStatus(followerUserId: UUID, status: FollowStatus): Long
    fun findAllByIdFollowingUserIdAndStatusOrderByUpdatedAtDesc(followingUserId: UUID, status: FollowStatus): List<Follow>
    fun findAllByIdFollowerUserIdAndStatusOrderByUpdatedAtDesc(followerUserId: UUID, status: FollowStatus): List<Follow>
}

interface BlockRepository : JpaRepository<Block, BlockId> {
    fun existsByIdBlockerUserIdAndIdBlockedUserId(blockerUserId: UUID, blockedUserId: UUID): Boolean
    fun findAllByIdBlockerUserIdOrderByCreatedAtDesc(blockerUserId: UUID): List<Block>
}

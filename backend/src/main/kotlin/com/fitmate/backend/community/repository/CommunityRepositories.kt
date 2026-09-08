package com.fitmate.backend.community.repository

import com.fitmate.backend.community.domain.CommunityPost
import com.fitmate.backend.community.domain.CommunityPostComment
import com.fitmate.backend.community.domain.CommunityPostLike
import com.fitmate.backend.community.domain.CommunityPostLikeId
import com.fitmate.backend.community.domain.CommunityPostMedia
import com.fitmate.backend.community.domain.CommunityPostMediaId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommunityPostRepository : JpaRepository<CommunityPost, UUID> {
    fun findByIdAndDeletedAtIsNull(id: UUID): CommunityPost?
    fun findAllByDeletedAtIsNullOrderByCreatedAtDesc(pageable: Pageable): List<CommunityPost>
}

interface CommunityPostMediaRepository : JpaRepository<CommunityPostMedia, CommunityPostMediaId> {
    fun findAllByPostIdOrderByDisplayOrderAsc(postId: UUID): List<CommunityPostMedia>
    fun existsByMediaId(mediaId: UUID): Boolean
    fun deleteAllByPostId(postId: UUID)
}

interface CommunityPostLikeRepository : JpaRepository<CommunityPostLike, CommunityPostLikeId> {
    fun countByPostId(postId: UUID): Long
    fun existsByPostIdAndUserId(postId: UUID, userId: UUID): Boolean
}

interface CommunityPostCommentRepository : JpaRepository<CommunityPostComment, UUID> {
    fun countByPostIdAndDeletedAtIsNull(postId: UUID): Long
    fun findAllByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId: UUID): List<CommunityPostComment>
    fun findByIdAndDeletedAtIsNull(id: UUID): CommunityPostComment?
}

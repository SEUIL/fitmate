package com.fitmate.backend.community.api

import com.fitmate.backend.community.domain.PostVisibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateCommunityPostRequest(
    @field:Size(max = 2000) val content: String = "",
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    @field:Size(max = 10) val mediaIds: List<UUID> = emptyList(),
)

data class UpdateCommunityPostRequest(
    @field:Size(max = 2000) val content: String = "",
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    @field:Size(max = 10) val mediaIds: List<UUID> = emptyList(),
    val version: Long,
)

data class CreateCommunityCommentRequest(@field:NotBlank @field:Size(max = 1000) val content: String)

data class CommunityAuthorResponse(val userId: UUID, val nickname: String, val displayName: String)

data class CommunityPostResponse(
    val postId: UUID,
    val author: CommunityAuthorResponse,
    val content: String,
    val visibility: PostVisibility,
    val mediaUrls: List<String>,
    val likedByMe: Boolean,
    val likeCount: Long,
    val commentCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class CommunityCommentResponse(
    val commentId: UUID,
    val author: CommunityAuthorResponse,
    val content: String,
    val createdAt: Instant,
)

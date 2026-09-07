package com.fitmate.backend.community.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.io.Serializable
import java.util.UUID

enum class PostVisibility { PUBLIC, MUTUAL }

@Entity
@Table(name = "community_posts")
class CommunityPost(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "author_user_id", nullable = false, columnDefinition = "BINARY(16)") var authorUserId: UUID? = null,
    @Column(name = "content", nullable = false, length = 2000) var content: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "visibility", nullable = false, length = 16) var visibility: PostVisibility = PostVisibility.PUBLIC,
    @Column(name = "deleted_at") var deletedAt: Instant? = null,
    @Version @Column(name = "version", nullable = false) var version: Long = 0,
) : BaseTimeEntity()

@Entity
@Table(name = "community_post_media")
@jakarta.persistence.IdClass(CommunityPostMediaId::class)
class CommunityPostMedia(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "post_id", columnDefinition = "BINARY(16)") var postId: UUID? = null,
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "media_id", columnDefinition = "BINARY(16)") var mediaId: UUID? = null,
    @Column(name = "display_order", nullable = false) var displayOrder: Int = 0,
) : BaseTimeEntity()

@Entity
@Table(name = "community_post_likes")
@jakarta.persistence.IdClass(CommunityPostLikeId::class)
class CommunityPostLike(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "post_id", columnDefinition = "BINARY(16)") var postId: UUID? = null,
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
) : BaseTimeEntity()

data class CommunityPostMediaId(var postId: UUID? = null, var mediaId: UUID? = null) : Serializable
data class CommunityPostLikeId(var postId: UUID? = null, var userId: UUID? = null) : Serializable

@Entity
@Table(name = "community_post_comments")
class CommunityPostComment(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "post_id", nullable = false, columnDefinition = "BINARY(16)") var postId: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "author_user_id", nullable = false, columnDefinition = "BINARY(16)") var authorUserId: UUID? = null,
    @Column(name = "content", nullable = false, length = 1000) var content: String = "",
    @Column(name = "deleted_at") var deletedAt: Instant? = null,
) : BaseTimeEntity()

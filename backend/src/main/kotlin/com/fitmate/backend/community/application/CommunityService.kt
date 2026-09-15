package com.fitmate.backend.community.application

import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.community.api.CommunityAuthorResponse
import com.fitmate.backend.community.api.CommunityCommentResponse
import com.fitmate.backend.community.api.CommunityPostResponse
import com.fitmate.backend.community.api.CreateCommunityCommentRequest
import com.fitmate.backend.community.api.CreateCommunityPostRequest
import com.fitmate.backend.community.api.UpdateCommunityPostRequest
import com.fitmate.backend.community.domain.CommunityPost
import com.fitmate.backend.community.domain.CommunityPostComment
import com.fitmate.backend.community.domain.CommunityPostLike
import com.fitmate.backend.community.domain.CommunityPostLikeId
import com.fitmate.backend.community.domain.CommunityPostMedia
import com.fitmate.backend.community.domain.CommunityPostMediaId
import com.fitmate.backend.community.domain.PostVisibility
import com.fitmate.backend.community.repository.CommunityPostCommentRepository
import com.fitmate.backend.community.repository.CommunityPostLikeRepository
import com.fitmate.backend.community.repository.CommunityPostMediaRepository
import com.fitmate.backend.community.repository.CommunityPostRepository
import com.fitmate.backend.media.application.MediaAssetService
import com.fitmate.backend.media.domain.MediaPurpose
import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.member.domain.UserProfile
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.social.repository.FollowRepository
import com.fitmate.backend.notification.application.NotificationService
import com.fitmate.backend.notification.domain.NotificationType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class CommunityService(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
    private val posts: CommunityPostRepository,
    private val postMedia: CommunityPostMediaRepository,
    private val likes: CommunityPostLikeRepository,
    private val comments: CommunityPostCommentRepository,
    private val follows: FollowRepository,
    private val blocks: BlockRepository,
    private val mediaAssets: MediaAssetService,
    private val notifications: NotificationService,
    private val ruleEngine: com.fitmate.backend.admin.application.ModerationRuleEngine,
) {
    @Transactional
    fun create(userId: UUID, request: CreateCommunityPostRequest): CommunityPostResponse {
        ruleEngine.beginWrite(userId)
        activeProfile(userId)
        validateContent(request.content, request.mediaIds)
        val post = posts.saveAndFlush(CommunityPost(UUID.randomUUID(), userId, request.content.trim(), request.visibility))
        replaceMedia(userId, requireNotNull(post.id), request.mediaIds)
        inspectRules(userId, post, true)
        return response(userId, post)
    }

    @Transactional
    fun update(userId: UUID, postId: UUID, request: UpdateCommunityPostRequest): CommunityPostResponse {
        ruleEngine.beginWrite(userId)
        val post = ownedPost(userId, postId)
        if (post.version != request.version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        val mediaIds = request.mediaIds ?: postMedia.findAllByPostIdOrderByDisplayOrderAsc(postId).map { requireNotNull(it.mediaId) }
        validateContent(request.content, mediaIds)
        post.content = request.content.trim()
        post.visibility = request.visibility
        posts.saveAndFlush(post)
        request.mediaIds?.let { replacement ->
            postMedia.deleteAllByPostId(postId)
            replaceMedia(userId, postId, replacement)
        }
        inspectRules(userId, post, false)
        return response(userId, post)
    }

    @Transactional
    fun delete(userId: UUID, postId: UUID) {
        val post = ownedPost(userId, postId)
        post.deletedAt = Instant.now()
        posts.saveAndFlush(post)
        inspectRules(userId, post, false)
    }

    @Transactional(readOnly = true)
    fun detail(viewerId: UUID, postId: UUID): CommunityPostResponse = response(viewerId, visiblePost(viewerId, postId))

    @Transactional(readOnly = true)
    fun feed(viewerId: UUID, followingOnly: Boolean, limit: Int, cursor: String?): CursorPageResponse<CommunityPostResponse> {
        activeProfile(viewerId)
        val safeLimit = limit.coerceIn(1, 50)
        val offset = decodeCursor(cursor)
        val candidates = posts.findAllByDeletedAtIsNullOrderByCreatedAtDesc(communityFeedWindow(offset, safeLimit))
        val visible = candidates.take(safeLimit).filter { post ->
            val authorId = requireNotNull(post.authorUserId)
            canView(viewerId, post) && (!followingOnly || authorId == viewerId || follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(viewerId, authorId, FollowStatus.ACCEPTED))
        }
        val page = visible.take(safeLimit).map { response(viewerId, it) }
        val hasNext = candidates.size > safeLimit
        return CursorPageResponse(page, if (hasNext) encodeCursor(offset + safeLimit) else null, hasNext)
    }

    @Transactional
    fun like(userId: UUID, postId: UUID): CommunityPostResponse {
        visiblePost(userId, postId)
        val id = CommunityPostLikeId(postId, userId)
        if (!likes.existsById(id)) likes.saveAndFlush(CommunityPostLike(postId, userId))
        val post = visiblePost(userId, postId)
        notifications.community(requireNotNull(post.authorUserId), userId, NotificationType.POST_LIKED, "POST", postId, "게시물을 좋아합니다.")
        return response(userId, post)
    }

    @Transactional
    fun unlike(userId: UUID, postId: UUID): CommunityPostResponse {
        visiblePost(userId, postId)
        likes.deleteById(CommunityPostLikeId(postId, userId))
        return response(userId, visiblePost(userId, postId))
    }

    @Transactional(readOnly = true)
    fun comments(viewerId: UUID, postId: UUID): List<CommunityCommentResponse> {
        visiblePost(viewerId, postId)
        return comments.findAllByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId).mapNotNull { comment ->
            val profile = profiles.findById(requireNotNull(comment.authorUserId)).orElse(null) ?: return@mapNotNull null
            if (profile.deletedAt != null) return@mapNotNull null
            CommunityCommentResponse(requireNotNull(comment.id), author(profile), comment.content, comment.createdAt)
        }
    }

    @Transactional
    fun addComment(userId: UUID, postId: UUID, request: CreateCommunityCommentRequest): CommunityCommentResponse {
        ruleEngine.beginWrite(userId)
        val profile = activeProfile(userId)
        visiblePost(userId, postId)
        val comment = comments.saveAndFlush(CommunityPostComment(UUID.randomUUID(), postId, userId, request.content.trim()))
        val post = visiblePost(userId, postId)
        notifications.community(requireNotNull(post.authorUserId), userId, NotificationType.POST_COMMENTED, "POST", postId, "게시물에 댓글을 남겼습니다.")
        ruleEngine.inspect(userId, com.fitmate.backend.admin.domain.RuleScope.COMMENT, requireNotNull(comment.id), comment.content,
            post.visibility == PostVisibility.PUBLIC && profile.visibility == ProfileVisibility.PUBLIC &&
                profiles.findById(requireNotNull(post.authorUserId)).orElse(null)?.visibility == ProfileVisibility.PUBLIC, true)
        return CommunityCommentResponse(requireNotNull(comment.id), author(profile), comment.content, comment.createdAt)
    }

    @Transactional
    fun deleteComment(userId: UUID, postId: UUID, commentId: UUID) {
        visiblePost(userId, postId)
        val comment = comments.findByIdAndDeletedAtIsNull(commentId)
            ?.takeIf { it.postId == postId }
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "댓글을 찾을 수 없습니다.")
        val post = posts.findByIdAndDeletedAtIsNull(postId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        if (comment.authorUserId != userId && post.authorUserId != userId) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
        comment.deletedAt = Instant.now()
        comments.saveAndFlush(comment)
        ruleEngine.inspect(requireNotNull(comment.authorUserId), com.fitmate.backend.admin.domain.RuleScope.COMMENT, commentId, "", false, false)
    }

    private fun inspectRules(userId: UUID, post: CommunityPost, created: Boolean) {
        ruleEngine.inspect(userId, com.fitmate.backend.admin.domain.RuleScope.POST, requireNotNull(post.id), post.content,
            post.deletedAt == null && post.visibility == PostVisibility.PUBLIC &&
                profiles.findById(userId).orElse(null)?.visibility == ProfileVisibility.PUBLIC, created)
    }

    @Transactional(readOnly = true)
    fun openPostMedia(viewerId: UUID, postId: UUID, mediaId: UUID) = mediaAssets.openForAuthorizedReader(
        mediaId.also {
            visiblePost(viewerId, postId)
            if (!postMedia.existsById(CommunityPostMediaId(postId, mediaId))) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        },
    )

    private fun response(viewerId: UUID, post: CommunityPost): CommunityPostResponse {
        val postId = requireNotNull(post.id)
        val author = profile(requireNotNull(post.authorUserId))
        return CommunityPostResponse(
            postId = postId,
            author = author(author),
            content = post.content,
            visibility = post.visibility,
            mediaUrls = postMedia.findAllByPostIdOrderByDisplayOrderAsc(postId).map { "/api/v1/community/posts/$postId/media/${it.mediaId}" },
            likedByMe = likes.existsByPostIdAndUserId(postId, viewerId),
            likeCount = likes.countByPostId(postId),
            commentCount = comments.countByPostIdAndDeletedAtIsNull(postId),
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            version = post.version,
        )
    }

    private fun replaceMedia(userId: UUID, postId: UUID, mediaIds: List<UUID>) {
        mediaIds.distinct().forEachIndexed { order, mediaId ->
            mediaAssets.requireOwnedActive(userId, mediaId, MediaPurpose.COMMUNITY_POST)
            postMedia.save(CommunityPostMedia(postId, mediaId, order))
        }
    }

    private fun visiblePost(viewerId: UUID, postId: UUID): CommunityPost {
        val post = posts.findByIdAndDeletedAtIsNull(postId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "게시물을 찾을 수 없습니다.")
        if (!canView(viewerId, post)) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "게시물을 찾을 수 없습니다.")
        return post
    }

    private fun canView(viewerId: UUID, post: CommunityPost): Boolean {
        val authorId = requireNotNull(post.authorUserId)
        if (isBlockedEitherDirection(viewerId, authorId)) return false
        if (viewerId == authorId || post.visibility == PostVisibility.PUBLIC) return true
        return follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(viewerId, authorId, FollowStatus.ACCEPTED) &&
            follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(authorId, viewerId, FollowStatus.ACCEPTED)
    }

    private fun ownedPost(userId: UUID, postId: UUID): CommunityPost =
        posts.findByIdAndDeletedAtIsNull(postId)?.takeIf { it.authorUserId == userId }
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "게시물을 찾을 수 없습니다.")

    private fun activeProfile(userId: UUID): UserProfile {
        val user = users.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (user.accountStatus != AccountStatus.ACTIVE) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        return profile(userId)
    }

    private fun profile(userId: UUID): UserProfile = profiles.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        .also { if (it.deletedAt != null) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }

    private fun author(profile: UserProfile) = CommunityAuthorResponse(requireNotNull(profile.userId), profile.nickname, profile.displayName)

    private fun isBlockedEitherDirection(first: UUID, second: UUID): Boolean =
        blocks.existsByIdBlockerUserIdAndIdBlockedUserId(first, second) || blocks.existsByIdBlockerUserIdAndIdBlockedUserId(second, first)

    private fun validateContent(content: String, mediaIds: List<UUID>) {
        if (content.isBlank() && mediaIds.isEmpty()) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "내용 또는 이미지를 입력해 주세요.")
        if (mediaIds.size != mediaIds.distinct().size) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "이미지는 중복해 첨부할 수 없습니다.")
    }

    private fun encodeCursor(offset: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(offset.toString().toByteArray())

    private fun decodeCursor(cursor: String?): Int = try {
        (cursor?.let { Base64.getUrlDecoder().decode(it).decodeToString().toInt() } ?: 0).also {
            require(it in 0..(Int.MAX_VALUE - 50))
        }
    } catch (_: IllegalArgumentException) {
        throw ApiException(ApiErrorCode.VALIDATION_ERROR, "페이지 커서가 올바르지 않습니다.")
    }
}

/** Cursor denotes rows consumed, not Spring Data's page number. One row is lookahead only. */
internal fun communityFeedWindow(offset: Int, limit: Int): PageRequest =
    object : PageRequest(0, limit + 1, Sort.unsorted()) {
        override fun getOffset(): Long = offset.toLong()
    }

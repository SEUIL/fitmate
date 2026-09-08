package com.fitmate.backend.mate.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.mate.domain.MatePost
import com.fitmate.backend.mate.domain.MateVisibility
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.social.repository.FollowRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MateAccessPolicy(private val follows: FollowRepository, private val blocks: BlockRepository) {
    fun requireVisible(post: MatePost, viewer: UUID) {
        if (!isVisible(post, viewer)) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
    }

    fun isVisible(post: MatePost, viewer: UUID): Boolean {
        val author = requireNotNull(post.authorId)
        if (author == viewer) return true
        val blocked = blocks.existsByIdBlockerUserIdAndIdBlockedUserId(viewer, author) ||
            blocks.existsByIdBlockerUserIdAndIdBlockedUserId(author, viewer)
        val mutual = post.visibility == MateVisibility.PUBLIC ||
            (follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(viewer, author, FollowStatus.ACCEPTED) &&
                follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(author, viewer, FollowStatus.ACCEPTED))
        return !blocked && mutual
    }
}

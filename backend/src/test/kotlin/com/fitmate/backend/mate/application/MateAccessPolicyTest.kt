package com.fitmate.backend.mate.application

import com.fitmate.backend.mate.domain.MatePost
import com.fitmate.backend.mate.domain.MateVisibility
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.social.repository.FollowRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class MateAccessPolicyTest {
    private val follows = mock(FollowRepository::class.java)
    private val blocks = mock(BlockRepository::class.java)
    private val policy = MateAccessPolicy(follows, blocks)
    private val author = UUID.randomUUID()
    private val viewer = UUID.randomUUID()

    @Test fun `public posts are visible but a block in either direction denies access`() {
        val post = MatePost(authorId = author)
        assertTrue(policy.isVisible(post, viewer))
        `when`(blocks.existsByIdBlockerUserIdAndIdBlockedUserId(author, viewer)).thenReturn(true)
        assertFalse(policy.isVisible(post, viewer))
        assertTrue(policy.isVisible(post, author))
    }

    @Test fun `mutual visibility requires both accepted follows`() {
        val post = MatePost(authorId = author, visibility = MateVisibility.MUTUAL)
        assertFalse(policy.isVisible(post, viewer))
        `when`(follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(viewer, author, FollowStatus.ACCEPTED)).thenReturn(true)
        assertFalse(policy.isVisible(post, viewer))
        `when`(follows.existsByIdFollowerUserIdAndIdFollowingUserIdAndStatus(author, viewer, FollowStatus.ACCEPTED)).thenReturn(true)
        assertTrue(policy.isVisible(post, viewer))
        `when`(blocks.existsByIdBlockerUserIdAndIdBlockedUserId(viewer, author)).thenReturn(true)
        assertFalse(policy.isVisible(post, viewer))
    }
}

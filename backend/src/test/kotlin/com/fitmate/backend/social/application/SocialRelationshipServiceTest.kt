package com.fitmate.backend.social.application

import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.domain.User
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.member.domain.UserProfile
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.social.domain.Follow
import com.fitmate.backend.social.domain.FollowId
import com.fitmate.backend.social.domain.FollowStatus
import com.fitmate.backend.social.repository.FollowRepository
import com.fitmate.backend.social.repository.BlockRepository
import com.fitmate.backend.notification.application.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class SocialRelationshipServiceTest {
    private val users = mock(UserRepository::class.java)
    private val profiles = mock(UserProfileRepository::class.java)
    private val follows = mock(FollowRepository::class.java)
    private val blocks = mock(BlockRepository::class.java)
    private val notifications = mock(NotificationService::class.java)
    private val now = Instant.parse("2026-09-07T00:00:00Z")
    private val service = SocialRelationshipService(users, profiles, follows, blocks, Clock.fixed(now, ZoneOffset.UTC), notifications)
    private val viewerId = UUID.randomUUID()
    private val targetId = UUID.randomUUID()
    private val target = UserProfile(userId = targetId, nickname = "mate", displayName = "Mate")

    @BeforeEach
    fun setUp() {
        `when`(users.findById(viewerId)).thenReturn(Optional.of(User(id = viewerId, accountStatus = AccountStatus.ACTIVE)))
        `when`(users.findById(targetId)).thenReturn(Optional.of(User(id = targetId, accountStatus = AccountStatus.ACTIVE)))
        `when`(profiles.findById(targetId)).thenReturn(Optional.of(target))
        `when`(profiles.findById(viewerId)).thenReturn(Optional.of(UserProfile(userId = viewerId, nickname = "me", displayName = "Me")))
        `when`(follows.findByIdFollowerUserIdAndIdFollowingUserId(viewerId, targetId)).thenReturn(null)
        `when`(follows.findByIdFollowerUserIdAndIdFollowingUserId(targetId, viewerId)).thenReturn(null)
    }

    @Test
    fun `public account follow is accepted immediately`() {
        `when`(follows.findForUpdate(viewerId, targetId)).thenReturn(null)

        val response = service.follow(viewerId, targetId)

        assertEquals(FollowStatus.ACCEPTED, response.status)
        val saved = ArgumentCaptor.forClass(Follow::class.java)
        verify(follows).saveAndFlush(saved.capture())
        assertEquals(FollowStatus.ACCEPTED, saved.value.status)
        assertEquals(now, saved.value.respondedAt)
    }

    @Test
    fun `private account follow remains pending until accepted`() {
        target.visibility = ProfileVisibility.PRIVATE
        `when`(follows.findForUpdate(viewerId, targetId)).thenReturn(null)

        val response = service.follow(viewerId, targetId)

        assertEquals(FollowStatus.PENDING, response.status)
        assertNull(response.relationship.followingStatus)
        val saved = ArgumentCaptor.forClass(Follow::class.java)
        verify(follows).saveAndFlush(saved.capture())
        assertEquals(FollowStatus.PENDING, saved.value.status)
        assertNull(saved.value.respondedAt)
    }

    @Test
    fun `private profile hides bio before approved follow and reveals it afterwards`() {
        target.visibility = ProfileVisibility.PRIVATE
        target.bio = "꾸준히 운동해요"
        val pending = Follow(FollowId(viewerId, targetId), FollowStatus.PENDING)
        `when`(follows.findByIdFollowerUserIdAndIdFollowingUserId(viewerId, targetId)).thenReturn(pending)

        val before = service.profile(viewerId, targetId)

        assertFalse(before.canViewProfile)
        assertNull(before.bio)
        pending.status = FollowStatus.ACCEPTED
        val after = service.profile(viewerId, targetId)
        assertTrue(after.canViewProfile)
        assertEquals("꾸준히 운동해요", after.bio)
    }

    @Test
    fun `recipient can accept a pending follow request`() {
        val pending = Follow(FollowId(viewerId, targetId), FollowStatus.PENDING)
        `when`(follows.findForUpdate(viewerId, targetId)).thenReturn(pending)
        `when`(follows.findByIdFollowerUserIdAndIdFollowingUserId(viewerId, targetId)).thenReturn(pending)
        `when`(follows.findByIdFollowerUserIdAndIdFollowingUserId(targetId, viewerId)).thenReturn(null)

        val response = service.respond(targetId, viewerId, accepted = true)

        assertEquals(FollowStatus.ACCEPTED, pending.status)
        assertEquals(FollowStatus.ACCEPTED, response.relationship.followedByStatus)
        verify(follows).saveAndFlush(pending)
    }

    @Test
    fun `unfollow changes only the active relation to canceled`() {
        val accepted = Follow(FollowId(viewerId, targetId), FollowStatus.ACCEPTED)
        `when`(follows.findForUpdate(viewerId, targetId)).thenReturn(accepted)
        `when`(follows.findByIdFollowerUserIdAndIdFollowingUserId(viewerId, targetId)).thenReturn(accepted)

        val response = service.cancelOrUnfollow(viewerId, targetId)

        assertEquals(FollowStatus.CANCELED, accepted.status)
        assertEquals(FollowStatus.CANCELED, response.status)
        verify(follows).saveAndFlush(accepted)
    }
}

package com.fitmate.backend.social.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

enum class FollowStatus { PENDING, ACCEPTED, REJECTED, CANCELED }

@Embeddable
data class FollowId(
    @Column(name = "follower_user_id", columnDefinition = "BINARY(16)")
    var followerUserId: UUID? = null,
    @Column(name = "following_user_id", columnDefinition = "BINARY(16)")
    var followingUserId: UUID? = null,
) : Serializable

@Entity
@Table(name = "social_follows")
class Follow(
    @EmbeddedId var id: FollowId = FollowId(),
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    var status: FollowStatus = FollowStatus.PENDING,
    @Column(name = "responded_at") var respondedAt: Instant? = null,
) : BaseTimeEntity()

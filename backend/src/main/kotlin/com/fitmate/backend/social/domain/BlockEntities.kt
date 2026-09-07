package com.fitmate.backend.social.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

@Embeddable
data class BlockId(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "blocker_user_id", columnDefinition = "BINARY(16)")
    var blockerUserId: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "blocked_user_id", columnDefinition = "BINARY(16)")
    var blockedUserId: UUID? = null,
) : Serializable

@Entity
@Table(name = "social_blocks")
class Block(
    @EmbeddedId var id: BlockId = BlockId(),
) : BaseTimeEntity()

package com.fitmate.backend.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class UserCustomSportId(
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "normalized_name", nullable = false, length = 50) var normalizedName: String = "",
) : Serializable

@Entity
@Table(name = "core_user_custom_sports")
class UserCustomSport(
    @EmbeddedId var id: UserCustomSportId = UserCustomSportId(),
    @Column(name = "name", nullable = false, length = 50) var name: String = "",
    @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant = Instant.EPOCH,
)

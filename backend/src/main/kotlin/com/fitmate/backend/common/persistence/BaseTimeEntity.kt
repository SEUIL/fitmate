package com.fitmate.backend.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant

@MappedSuperclass
abstract class BaseTimeEntity(
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
) {

    @PrePersist
    fun setCreatedTimestamps() {
        val now = Instant.now()
        if (createdAt == Instant.EPOCH) {
            createdAt = now
        }
        if (updatedAt == Instant.EPOCH) {
            updatedAt = now
        }
    }

    @PreUpdate
    fun setUpdatedTimestamp() {
        updatedAt = Instant.now()
    }
}

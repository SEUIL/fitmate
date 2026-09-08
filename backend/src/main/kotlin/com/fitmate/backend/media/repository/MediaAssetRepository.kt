package com.fitmate.backend.media.repository

import com.fitmate.backend.media.domain.MediaAsset
import com.fitmate.backend.media.domain.MediaStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MediaAssetRepository : JpaRepository<MediaAsset, UUID> {
    fun findByIdAndOwnerUserIdAndStatus(id: UUID, ownerUserId: UUID, status: MediaStatus): MediaAsset?
    fun findByIdAndStatus(id: UUID, status: MediaStatus): MediaAsset?
}

package com.fitmate.backend.media.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

enum class MediaPurpose { PROFILE, COMMUNITY_POST, DM, INBODY }
enum class MediaStatus { ACTIVE, DELETED }

@Entity
@Table(name = "media_assets")
class MediaAsset(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "owner_user_id", nullable = false, columnDefinition = "BINARY(16)")
    var ownerUserId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "purpose", nullable = false, length = 32)
    var purpose: MediaPurpose = MediaPurpose.COMMUNITY_POST,
    @Column(name = "original_filename", length = 255) var originalFilename: String? = null,
    @Column(name = "content_type", nullable = false, length = 100) var contentType: String = "image/jpeg",
    @Column(name = "byte_size", nullable = false) var byteSize: Long = 0,
    @Column(name = "storage_key", nullable = false, length = 255) var storageKey: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    var status: MediaStatus = MediaStatus.ACTIVE,
) : BaseTimeEntity()

package com.fitmate.backend.media.api

import com.fitmate.backend.media.domain.MediaPurpose
import java.util.UUID

data class MediaUploadResponse(
    val mediaId: UUID,
    val purpose: MediaPurpose,
    val contentType: String,
    val byteSize: Long,
    val url: String,
)

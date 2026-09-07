package com.fitmate.backend.media.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fitmate.media")
data class MediaStorageProperties(
    var storageDir: String = "./data/media",
    var maxImageBytes: Long = 10L * 1024 * 1024,
)

package com.fitmate.backend.media.application

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MediaStorageProperties::class)
class MediaStorageConfiguration

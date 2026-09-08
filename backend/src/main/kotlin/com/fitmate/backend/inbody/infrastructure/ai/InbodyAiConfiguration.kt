package com.fitmate.backend.inbody.infrastructure.ai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@EnableAsync
@EnableConfigurationProperties(InbodyAiProperties::class)
class InbodyAiConfiguration

package com.fitmate.backend.recommendation.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fitmate.workout-recommendation-ai")
data class WorkoutRecommendationAiProperties(
    var enabled: Boolean = true,
    var apiKey: String = "",
    var model: String = "gpt-5.6-luna",
    var baseUrl: String = "https://api.openai.com",
    var timeoutSeconds: Long = 15,
    var directConnection: Boolean = false,
)

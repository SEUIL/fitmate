package com.fitmate.backend.recommendation.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(WorkoutRecommendationAiProperties::class)
class WorkoutRecommendationAiConfiguration {
    @org.springframework.context.annotation.Bean("workoutPlanExecutor")
    fun workoutPlanExecutor(): org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor =
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor().apply {
            corePoolSize = 2; maxPoolSize = 2; queueCapacity = 8
            setThreadNamePrefix("workout-plan-")
            setWaitForTasksToCompleteOnShutdown(false)
        }
}

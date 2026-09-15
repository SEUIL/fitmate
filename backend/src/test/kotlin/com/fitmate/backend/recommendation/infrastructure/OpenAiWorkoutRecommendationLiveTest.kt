package com.fitmate.backend.recommendation.infrastructure

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/** Paid, opt-in diagnostic. Sends only synthetic recommendation text and no member or InBody records. */
@EnabledIfEnvironmentVariable(named = "FITMATE_LIVE_OPENAI_TEST", matches = "true")
class OpenAiWorkoutRecommendationLiveTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WorkoutRecommendationAiProperties::class)
    class LiveConfig

    @Test
    fun `Luna writes one recommendation summary using local configuration`() {
        val application = SpringApplication(LiveConfig::class.java).apply {
            webApplicationType = WebApplicationType.NONE
        }
        application.run("--spring.main.banner-mode=off").use { context ->
            val properties = context.getBean(WorkoutRecommendationAiProperties::class.java)
            check(properties.enabled) { "Workout recommendation AI is disabled; no request sent." }
            check(properties.apiKey.isNotBlank()) { "OpenAI configuration is missing; no request sent." }
            val narrator = OpenAiWorkoutRecommendationNarrator(RestClient.builder(), properties)
            val summary = narrator.narrate(
                name = "테스트 전신 루틴",
                reasons = listOf("주 3일, 회당 45분으로 운동 계획을 구성했어요."),
                cardioMinutes = 0,
                inbody = null,
            )
            assertTrue(!summary.isNullOrBlank(), "Luna did not return a recommendation summary.")
        }
    }
}

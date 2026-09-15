package com.fitmate.backend.recommendation.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.inbody.domain.InbodyRecord
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.Proxy
import java.time.Duration

@Component
class OpenAiWorkoutRecommendationNarrator(
    restClientBuilder: RestClient.Builder,
    private val properties: WorkoutRecommendationAiProperties,
) : WorkoutRecommendationNarrator {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = restClientBuilder
        .requestFactory(SimpleClientHttpRequestFactory().also {
            val timeout = Duration.ofSeconds(properties.timeoutSeconds)
            it.setConnectTimeout(timeout)
            it.setReadTimeout(timeout)
            if (properties.directConnection) it.setProxy(Proxy.NO_PROXY)
        })
        .baseUrl(properties.baseUrl)
        .build()

    override fun narrate(name: String, reasons: List<String>, cardioMinutes: Int, inbody: InbodyRecord?): String? {
        if (!properties.enabled || properties.apiKey.isBlank()) return null
        return try {
            val response = client.post()
                .uri("/v1/responses")
                .headers { it.setBearerAuth(properties.apiKey) }
                .body(requestBody(name, reasons, cardioMinutes, inbody))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, providerResponse ->
                    logger.warn("OpenAI workout recommendation narration rejected: status={}", providerResponse.statusCode.value())
                    throw WorkoutRecommendationNarrationException()
                }
                .body(JsonNode::class.java)
                ?: return null
            outputText(response)?.takeIf { it.length <= MAX_SUMMARY_LENGTH }
        } catch (exception: Exception) {
            if (exception !is WorkoutRecommendationNarrationException) {
                logger.warn("OpenAI workout recommendation narration unavailable: type={}", exception.javaClass.simpleName)
            }
            null
        }
    }

    private fun requestBody(name: String, reasons: List<String>, cardioMinutes: Int, inbody: InbodyRecord?): Map<String, Any> = mapOf(
        "model" to properties.model,
        "instructions" to INSTRUCTIONS,
        "input" to prompt(name, reasons, cardioMinutes, inbody),
        "max_output_tokens" to 160,
        "store" to false,
    )

    private fun prompt(name: String, reasons: List<String>, cardioMinutes: Int, inbody: InbodyRecord?): String = buildString {
        appendLine("추천 이름: $name")
        appendLine("유산소 시간: ${cardioMinutes}분")
        appendLine("추천 근거:")
        reasons.forEach { appendLine("- $it") }
        inbody?.let {
            appendLine("선택한 체성분 요약: 체지방률 ${it.bodyFatPercentage}%, 골격근량 ${it.skeletalMuscleMassKg}kg")
        }
    }

    private fun outputText(response: JsonNode): String? = response.path("output")
        .flatMap { it.path("content").toList() }
        .firstOrNull { it.path("type").asText() == "output_text" && it.hasNonNull("text") }
        ?.path("text")
        ?.asText()
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf(String::isNotBlank)

    private class WorkoutRecommendationNarrationException : RuntimeException()

    private companion object {
        const val MAX_SUMMARY_LENGTH = 240
        const val INSTRUCTIONS = """
            서버가 확정한 운동 추천을 사용자에게 안내하는 한국어 문장을 한두 문장으로 작성하세요.
            제공된 추천 이름, 유산소 시간, 추천 근거, 체성분 요약만 사용하세요.
            새 운동, 중량, 세트, 반복 수, 운동 시간, 식단을 제안하거나 기존 추천을 변경하지 마세요.
            질환을 진단하거나 치료·재활 조언을 하지 마세요. 공감하는 말투로 간결하게 작성하세요.
        """
    }
}

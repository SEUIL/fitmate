package com.fitmate.backend.inbody.infrastructure.ai.gemini

import com.fasterxml.jackson.databind.JsonNode
import com.fitmate.backend.inbody.domain.InbodyAiProvider
import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAiProperties
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalysisInput
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalyzer
import com.fitmate.backend.inbody.infrastructure.ai.InbodyProviderException
import com.fitmate.backend.inbody.infrastructure.ai.InbodyProviderResult
import com.fitmate.backend.inbody.infrastructure.ai.ProviderExtractedField
import org.springframework.http.HttpStatusCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.Proxy
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration
import java.util.Base64

@Component
class GeminiInbodyAnalyzer(
    restClientBuilder: RestClient.Builder,
    private val properties: InbodyAiProperties,
) : InbodyAnalyzer {
    override val provider = InbodyAiProvider.GEMINI
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = restClientBuilder
        .requestFactory(SimpleClientHttpRequestFactory().also {
            val timeout = Duration.ofSeconds(properties.gemini.timeoutSeconds)
            it.setConnectTimeout(timeout)
            it.setReadTimeout(timeout)
            if (properties.gemini.directConnection) it.setProxy(Proxy.NO_PROXY)
        })
        .baseUrl(properties.gemini.baseUrl)
        .build()

    override fun analyze(input: InbodyAnalysisInput): InbodyProviderResult {
        val config = properties.gemini
        if (config.apiKey.isBlank()) {
            throw InbodyProviderException("PROVIDER_NOT_CONFIGURED", "Gemini API 키가 설정되지 않았습니다.")
        }

        val response = try {
            client.post()
                .uri("/v1beta/models/{model}:generateContent", config.model)
                .header("x-goog-api-key", config.apiKey)
                .body(requestBody(input))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, providerResponse ->
                    val status = providerResponse.statusCode.value()
                    // Provider error bodies may echo credentials or sensitive input. Log only the status.
                    logger.warn("Gemini request rejected: status={}", status)
                    throw InbodyProviderException(
                        failureCode = when (status) {
                            429 -> "PROVIDER_RATE_LIMITED"
                            in 500..599 -> "PROVIDER_UNAVAILABLE"
                            else -> "PROVIDER_REQUEST_REJECTED"
                        },
                        message = "Gemini 판독 요청에 실패했습니다.",
                        retryable = status == 429 || status >= 500,
                    )
                }
                .body(JsonNode::class.java)
                ?: throw InbodyProviderException("INVALID_PROVIDER_RESPONSE", "Gemini 응답이 비어 있습니다.")
        } catch (exception: InbodyProviderException) {
            throw exception
        } catch (exception: RestClientException) {
            throw InbodyProviderException("PROVIDER_UNAVAILABLE", "Gemini에 연결하지 못했습니다.", true, exception)
        }

        val text = response.path("candidates").path(0).path("content").path("parts")
            .firstOrNull { it.hasNonNull("text") }
            ?.path("text")
            ?.asText()
            ?: throw InbodyProviderException("INVALID_PROVIDER_RESPONSE", "Gemini 판독 결과를 확인할 수 없습니다.")
        val parsed = runCatching { jackson.readTree(text) }.getOrElse {
            throw InbodyProviderException("INVALID_PROVIDER_RESPONSE", "Gemini 판독 결과 형식이 올바르지 않습니다.", cause = it)
        }
        val fields = parsed.path("fields").takeIf(JsonNode::isArray)?.map { field ->
            ProviderExtractedField(
                code = field.path("code").asText(),
                value = field.path("value").takeUnless(JsonNode::isNull)?.asText(),
                unit = field.path("unit").takeUnless(JsonNode::isNull)?.asText(),
                rawText = field.path("rawText").takeUnless(JsonNode::isNull)?.asText(),
            )
        } ?: throw InbodyProviderException("INVALID_PROVIDER_RESPONSE", "Gemini 판독 필드가 없습니다.")
        return InbodyProviderResult(config.model, fields)
    }

    private fun requestBody(input: InbodyAnalysisInput): Map<String, Any> = mapOf(
        "contents" to listOf(mapOf(
            "role" to "user",
            "parts" to listOf(
                mapOf("text" to PROMPT),
                mapOf("inlineData" to mapOf(
                    "mimeType" to input.contentType,
                    "data" to Base64.getEncoder().encodeToString(input.bytes),
                )),
            ),
        )),
        "generationConfig" to mapOf(
            "responseMimeType" to "application/json",
            "responseJsonSchema" to responseSchema,
            "temperature" to 0.1,
            "maxOutputTokens" to 4096,
        ),
    )

    private companion object {
        val jackson = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val fieldCodes = InbodyFieldCode.entries.map { it.name }
        val responseSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "fields" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "code" to mapOf("type" to "string", "enum" to fieldCodes),
                            "value" to mapOf("type" to listOf("string", "null")),
                            "unit" to mapOf("type" to listOf("string", "null")),
                            "rawText" to mapOf("type" to listOf("string", "null")),
                        ),
                        "required" to listOf("code", "value", "unit", "rawText"),
                        "additionalProperties" to false,
                    ),
                ),
            ),
            "required" to listOf("fields"),
            "additionalProperties" to false,
        )

        const val PROMPT = """
            이 이미지는 체성분 측정 결과표일 수 있다. 이미지에 직접 보이는 값만 추출한다.
            보이지 않거나 확실하지 않은 값은 value와 rawText를 null로 반환한다.
            값을 추측하거나 다른 항목으로 계산하지 않는다. 숫자의 소수점과 단위를 그대로 확인한다.
            이름, 전화번호, 회원번호, QR 코드 등 개인정보는 반환하지 않는다.
            가능한 모든 field code를 한 번씩 반환하되 이미지에 없는 항목은 null로 둔다.
            MEASUREMENT_DATE 값은 YYYY-MM-DD 형식으로 반환하고 나머지 값은 숫자 문자열로 반환한다.
        """
    }
}

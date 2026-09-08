package com.fitmate.backend.inbody.infrastructure.ai.gemini

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.infrastructure.ai.GeminiProperties
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAiProperties
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalysisInput
import com.fitmate.backend.inbody.infrastructure.ai.InbodyProviderException
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class GeminiInbodyAnalyzerTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `이미지를 Gemini 요청 형식으로 보내고 구조화 결과를 읽는다`() {
        val requestBody = AtomicReference<String>()
        val requestKey = AtomicReference<String>()
        val requestPath = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            requestPath.set(exchange.requestURI.path)
            requestKey.set(exchange.requestHeaders.getFirst("x-goog-api-key"))
            requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            val extracted = objectMapper.writeValueAsString(mapOf(
                "fields" to listOf(mapOf(
                    "code" to "WEIGHT_KG",
                    "value" to "68.2",
                    "unit" to "kg",
                    "rawText" to "체중 68.2 kg",
                )),
            ))
            val response = objectMapper.writeValueAsBytes(mapOf(
                "candidates" to listOf(mapOf(
                    "content" to mapOf("parts" to listOf(mapOf("text" to extracted))),
                )),
            ))
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val properties = InbodyAiProperties(gemini = GeminiProperties(
                apiKey = "test-key-not-a-secret",
                model = "gemini-test",
                baseUrl = "http://127.0.0.1:${server.address.port}",
            ))
            val analyzer = GeminiInbodyAnalyzer(RestClient.builder(), properties)

            val result = analyzer.analyze(InbodyAnalysisInput("image/jpeg", byteArrayOf(1, 2, 3)))

            assertThat(requestPath.get()).isEqualTo("/v1beta/models/gemini-test:generateContent")
            assertThat(requestKey.get()).isEqualTo("test-key-not-a-secret")
            assertThat(requestBody.get()).contains("inlineData", "AQID", "responseJsonSchema")
            assertThat(result.model).isEqualTo("gemini-test")
            assertThat(result.fields.single().code).isEqualTo(InbodyFieldCode.WEIGHT_KG.name)
            assertThat(result.fields.single().value).isEqualTo("68.2")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `API 키가 없으면 외부 요청 전에 명확한 설정 오류를 반환한다`() {
        val analyzer = GeminiInbodyAnalyzer(RestClient.builder(), InbodyAiProperties())

        assertThatThrownBy {
            analyzer.analyze(InbodyAnalysisInput("image/png", byteArrayOf(1)))
        }.isInstanceOf(InbodyProviderException::class.java)
            .extracting { (it as InbodyProviderException).failureCode }
            .isEqualTo("PROVIDER_NOT_CONFIGURED")
    }

    @Test
    fun `Gemini 오류 응답은 요청 거부 코드로 변환한다`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val response = """{\"error\":{\"message\":\"model is unavailable\"}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(404, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val properties = InbodyAiProperties(gemini = GeminiProperties(
                apiKey = "test-key-not-a-secret",
                baseUrl = "http://127.0.0.1:${server.address.port}",
            ))
            val analyzer = GeminiInbodyAnalyzer(RestClient.builder(), properties)

            assertThatThrownBy { analyzer.analyze(InbodyAnalysisInput("image/jpeg", byteArrayOf(1))) }
                .isInstanceOf(InbodyProviderException::class.java)
                .extracting { (it as InbodyProviderException).failureCode }
                .isEqualTo("PROVIDER_REQUEST_REJECTED")
        } finally {
            server.stop(0)
        }
    }
}

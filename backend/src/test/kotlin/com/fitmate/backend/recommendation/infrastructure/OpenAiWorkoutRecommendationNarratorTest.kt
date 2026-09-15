package com.fitmate.backend.recommendation.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fitmate.backend.inbody.domain.InbodyRecord
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OpenAiWorkoutRecommendationNarratorTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Luna에 최소 추천 정보만 보내고 요약 문장을 읽는다`() {
        val requestBody = AtomicReference<String>()
        val authorization = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            val response = objectMapper.writeValueAsBytes(mapOf(
                "output" to listOf(mapOf(
                    "type" to "message",
                    "content" to listOf(mapOf("type" to "output_text", "text" to "  오늘은 무리하지 않고 천천히 적응해 보세요.  ")),
                )),
            ))
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val narrator = OpenAiWorkoutRecommendationNarrator(
                RestClient.builder(),
                WorkoutRecommendationAiProperties(
                    apiKey = "test-key-not-a-secret",
                    model = "gpt-5.6-luna",
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                ),
            )
            val summary = narrator.narrate(
                name = "초급 전신 루틴",
                reasons = listOf("주 3일, 회당 45분으로 운동 계획을 구성했어요."),
                cardioMinutes = 15,
                inbody = InbodyRecord(
                    measuredDate = LocalDate.of(2026, 9, 1),
                    weightKg = BigDecimal("72.0"),
                    skeletalMuscleMassKg = BigDecimal("30.0"),
                    bodyFatPercentage = BigDecimal("22.0"),
                    optionalValuesJson = "{\"name\":\"전송하면 안 됨\"}",
                ),
            )

            assertThat(summary).isEqualTo("오늘은 무리하지 않고 천천히 적응해 보세요.")
            assertThat(authorization.get()).isEqualTo("Bearer test-key-not-a-secret")
            assertThat(requestBody.get()).contains(
                "\"model\":\"gpt-5.6-luna\"",
                "\"store\":false",
                "체지방률 22.0%",
                "골격근량 30.0kg",
            ).doesNotContain("전송하면 안 됨", "measuredDate")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `키가 없으면 외부 요청 없이 기본 추천을 유지한다`() {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()

        try {
            val narrator = OpenAiWorkoutRecommendationNarrator(
                RestClient.builder(),
                WorkoutRecommendationAiProperties(baseUrl = "http://127.0.0.1:${server.address.port}"),
            )

            assertThat(narrator.narrate("추천", emptyList(), 0, null)).isNull()
            assertThat(requests.get()).isZero()
        } finally {
            server.stop(0)
        }
    }
}

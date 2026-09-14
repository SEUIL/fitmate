package com.fitmate.backend.inbody.api

import com.fasterxml.jackson.databind.JsonNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import java.nio.file.Files
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
    "fitmate.inbody-ai.provider=MOCK",
])
class InbodyApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate

    companion object {
        private val mediaDir = Files.createTempDirectory("fitmate-inbody-api-test-")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
            registry.add("fitmate.media.storage-dir") { mediaDir.toString() }
        }
    }

    @Test
    fun `AI 누락값을 사용자가 채워 최종 기록으로 저장한다`() {
        val owner = signup()
        val stranger = signup()
        val mediaId = uploadInbodyImage(owner)

        val accepted = command(
            "/api/v1/inbody-analyses", HttpMethod.POST, owner, mapOf("mediaId" to mediaId),
        )
        assertEquals(HttpStatus.ACCEPTED, accepted.statusCode, accepted.body.toString())
        val analysisId = accepted.body!!["data"]["id"].asText()

        val analysis = awaitFinishedAnalysis(owner, analysisId)
        assertEquals("REVIEW_REQUIRED", analysis["status"].asText())
        val bodyFat = analysis["fields"].first { it["code"].asText() == "BODY_FAT_PERCENT" }
        assertThat(bodyFat["value"].isNull).isTrue()
        assertEquals("NOT_RECOGNIZED", bodyFat["status"].asText())
        assertEquals(HttpStatus.NOT_FOUND, command(
            "/api/v1/inbody-analyses/$analysisId", HttpMethod.GET, stranger, null,
        ).statusCode)

        val saved = command(
            "/api/v1/inbody-records", HttpMethod.POST, owner,
            mapOf(
                "analysisId" to analysisId,
                "measuredDate" to "2026-09-08",
                "weightKg" to 68.2,
                "skeletalMuscleMassKg" to 29.4,
                "bodyFatPercentage" to 18.7,
                "optionalValues" to mapOf("BMI" to 22.1),
            ),
        )
        assertEquals(HttpStatus.CREATED, saved.statusCode, saved.body.toString())
        assertEquals("AI_OCR", saved.body!!["data"]["sourceType"].asText())
        assertEquals(18.7, saved.body!!["data"]["bodyFatPercentage"].asDouble())

        val list = command("/api/v1/inbody-records", HttpMethod.GET, owner, null)
        assertEquals(HttpStatus.OK, list.statusCode)
        assertEquals(saved.body!!["data"]["id"].asText(), list.body!!["data"]["items"][0]["id"].asText())
    }

    private fun uploadInbodyImage(token: String): String {
        val resource = object : ByteArrayResource(byteArrayOf(1, 2, 3, 4)) {
            override fun getFilename() = "inbody.jpg"
        }
        val partHeaders = HttpHeaders().also { it.contentType = MediaType.IMAGE_JPEG }
        val multipart = LinkedMultiValueMap<String, Any>().also {
            it.add("file", HttpEntity(resource, partHeaders))
        }
        val headers = HttpHeaders().also {
            it.setBearerAuth(token)
            it.contentType = MediaType.MULTIPART_FORM_DATA
        }
        val response = http.exchange(
            "/api/v1/media?purpose=INBODY",
            HttpMethod.POST,
            HttpEntity(multipart, headers),
            JsonNode::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode, response.body.toString())
        return response.body!!["data"]["mediaId"].asText()
    }

    private fun awaitFinishedAnalysis(token: String, analysisId: String): JsonNode {
        repeat(50) {
            val response = command("/api/v1/inbody-analyses/$analysisId", HttpMethod.GET, token, null)
            assertEquals(HttpStatus.OK, response.statusCode, response.body.toString())
            val data = response.body!!["data"]
            if (data["status"].asText() in setOf("COMPLETED", "REVIEW_REQUIRED", "FAILED")) return data
            Thread.sleep(50)
        }
        error("인바디 판독이 제한 시간 안에 끝나지 않았습니다.")
    }

    private fun signup(): String {
        val body = mapOf(
            "email" to "inbody-${UUID.randomUUID()}@example.com",
            "password" to "test-password-123",
            "nickname" to "inbody-${UUID.randomUUID().toString().take(20)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf(
                "20000000-0000-0000-0000-000000000001",
                "20000000-0000-0000-0000-000000000002",
            ),
        )
        val response = http.postForEntity("/api/v1/auth/signup", body, JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode, response.body.toString())
        return response.body!!["data"]["tokens"]["accessToken"].asText()
    }

    private fun command(path: String, method: HttpMethod, token: String, body: Any?) = http.exchange(
        path,
        method,
        HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token) }),
        JsonNode::class.java,
    )
}

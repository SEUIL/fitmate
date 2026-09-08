package com.fitmate.backend.inbody.infrastructure.ai.gemini

import com.fitmate.backend.inbody.infrastructure.ai.InbodyAiProperties
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalysisInput
import com.fitmate.backend.inbody.infrastructure.ai.InbodyProviderException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Paid, opt-in diagnostic. No Android, HTTP controller, DB or automatic retry. */
@EnabledIfEnvironmentVariable(named = "FITMATE_LIVE_GEMINI_TEST", matches = "true")
class GeminiInbodyLiveTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InbodyAiProperties::class)
    class LiveConfig

    @Test
    fun `backend analyzer reads one synthetic report using local configuration`() {
        val application = SpringApplication(LiveConfig::class.java).apply {
            webApplicationType = WebApplicationType.NONE
            setAdditionalProfiles("local")
        }
        application.run("--spring.main.banner-mode=off").use { context ->
            val properties = context.getBean(InbodyAiProperties::class.java)
            check(properties.gemini.apiKey.isNotBlank()) { "Gemini configuration is missing; no request sent." }
            val analyzer = GeminiInbodyAnalyzer(RestClient.builder(), properties)
            val result = try {
                analyzer.analyze(InbodyAnalysisInput("image/png", syntheticReport()))
            } catch (failure: InbodyProviderException) {
                // Do not include provider response bodies, URLs or exception chains in evidence.
                throw AssertionError("Backend-only Gemini analysis failed: code=${failure.failureCode}")
            }
            val fields = result.fields.associate { it.code to it.value }
            assertEquals(68.2, requireNotNull(fields["WEIGHT_KG"]).toDouble(), 0.1)
            assertEquals(29.4, requireNotNull(fields["SKELETAL_MUSCLE_MASS_KG"]).toDouble(), 0.1)
            assertEquals(18.5, requireNotNull(fields["BODY_FAT_PERCENT"]).toDouble(), 0.1)
        }
    }

    private fun syntheticReport(): ByteArray {
        val image = BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color.BLACK
            graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 34)
            listOf("SYNTHETIC TEST - NOT A REAL PERSON", "Body Composition Results",
                "Measurement Date: 2026-09-08", "Height: 175.0 cm", "Weight: 68.2 kg",
                "Skeletal Muscle Mass: 29.4 kg", "Percent Body Fat: 18.5 %",
                "Body Mass Index (BMI): 22.3").forEachIndexed { index, line ->
                graphics.drawString(line, 40, 90 + index * 100)
            }
        } finally { graphics.dispose() }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
    }
}

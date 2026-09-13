package com.fitmate.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Costs money. Run only after explicit approval, exactly once; no analysis retry. */
class OpenAiInbodyDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun oneApprovedSyntheticReportAnalysisAndEditedSave() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("approvedOpenAiOnce") == "true")
        val api = FitMateApiClient(BuildConfig.API_BASE_URL)
        val unique = UUID.randomUUID().toString()
        api.authenticate("auth/signup", mapOf(
            "email" to "openai-device-$unique@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "AI검증-${unique.take(10)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }
        ))
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 35f }
        listOf("SYNTHETIC TEST - NOT A REAL PERSON", "Body Composition Results",
            "Measurement Date: 2026-09-08", "Height: 175.0 cm", "Weight: 68.2 kg",
            "Skeletal Muscle Mass: 29.4 kg", "Percent Body Fat: 18.5 %",
            "Body Mass Index (BMI): 22.3").forEachIndexed { i, line -> canvas.drawText(line, 40f, 90f + i * 100f, paint) }
        val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
        bitmap.recycle()
        val mediaId = api.uploadImage("INBODY", "synthetic-report.png", "image/png", bytes).text("mediaId")
        val server = ServerInbodyApi(api)
        val created = server.createAnalysis(mediaId) // Only external analysis request in this test.
        assertEquals("OPENAI", created.text("provider"))
        var result = created
        val deadline = System.currentTimeMillis() + 90_000
        while (result.text("status") !in setOf("COMPLETED", "REVIEW_REQUIRED", "FAILED") && System.currentTimeMillis() < deadline) {
            Thread.sleep(2_000)
            result = server.analysis(created.text("id"))
        }
        assertTrue("OpenAI terminal status=${result.text("status")}; code=${result.get("failure")?.takeIf { it.isJsonObject }?.asJsonObject?.text("code")}",
            result.text("status") in setOf("COMPLETED", "REVIEW_REQUIRED"))
        val fields = result.rows("fields").associate { it.text("code") to it.text("value") }
        assertEquals(68.2, requireNotNull(fields["WEIGHT_KG"]).toDouble(), 0.1)
        assertEquals(29.4, requireNotNull(fields["SKELETAL_MUSCLE_MASS_KG"]).toDouble(), 0.1)
        assertEquals(18.5, requireNotNull(fields["BODY_FAT_PERCENT"]).toDouble(), 0.1)
        val state = InbodyState().apply { analysisId = created.text("id"); source = "사진 판독"; phase = "판독 중" }
        compose.setContent { MaterialTheme { InbodyFlow(Modifier, state, server) } }
        compose.waitUntil(20_000) { state.phase == "검수" }
        captureReviewScreen()
        compose.onNodeWithText("68.2 kg").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("체중")).performTextReplacement("68.3")
        compose.onNodeWithText("완료").performClick()
        compose.onNodeWithText("확인이 필요한 수치를 검수했어요").performScrollTo().performClick()
        compose.waitUntil(5_000) { state.acknowledged }
        compose.onNodeWithText("검수 후 저장").performScrollTo().performClick()
        try {
            compose.waitUntil(30_000) { state.phase == "목록" && state.records.size == 1 }
        } catch (failure: Throwable) {
            throw AssertionError("검수 저장 화면 상태: phase=${state.phase}, busy=${state.busy}, error=${state.error}", failure)
        }
        val saved = server.records().single()
        assertEquals(created.text("id"), saved.text("analysisId"))
        assertEquals(68.3, saved.get("weightKg").asDouble, 0.01)
        assertEquals("AI_OCR", saved.text("sourceType"))
    }

    private fun captureReviewScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "openai-review-proof.png")
        FileOutputStream(output).use { stream ->
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}

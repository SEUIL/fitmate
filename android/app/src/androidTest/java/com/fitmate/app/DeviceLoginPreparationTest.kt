package com.fitmate.app.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.SessionTokenStore
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicitly prepares an authenticated test account for a requested manual device review. */
class DeviceLoginPreparationTest {
    @Test fun checkCurrentSessionEndpoints() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("checkCurrentSession") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = FitMateApiClient(BuildConfig.API_BASE_URL, tokenStore = SessionTokenStore(context))
        val results = listOf("users/me", "mate-posts?size=30&status=RECRUITING", "mate-posts?size=1").map { path ->
            try { api.request("GET", path); "$path: OK" }
            catch (failure: com.fitmate.app.data.remote.ApiFailure) { "$path: HTTP ${failure.status}" }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", results.joinToString("\n")) })
        assertTrue(results.joinToString("; "), results.all { it.endsWith(": OK") })
    }

    @Test fun prepareManualReviewLogin() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("prepareManualLogin") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = FitMateApiClient(BuildConfig.API_BASE_URL, tokenStore = SessionTokenStore(context))
        if (api.hasSession()) {
            api.request("GET", "users/me")
            return
        }
        val unique = UUID.randomUUID().toString()
        val email = "review-$unique@example.com"
        val secret = UUID.randomUUID().toString()
        api.authenticate("auth/signup", mapOf(
            "email" to email, "password" to secret,
            "nickname" to "직접확인-${unique.take(6)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to api.items("terms", false).filter { it.get("required").asBoolean }.map { it.text("id") }
        ))
        api.authenticate("auth/login", mapOf("email" to email, "password" to secret))
        api.request("GET", "users/me")
        assertTrue(api.hasSession())
    }
}

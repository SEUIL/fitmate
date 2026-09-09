package com.fitmate.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.ServerSocialApi
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Explicit opt-in: synthetic images and an isolated account, no personal gallery data. */
class CommunityPhotoIntegrationTest {
    @Test fun multiplePhotosSurviveReloadAndCanBeRemoved() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCommunity") == "true")
        val api = FitMateApiClient(BuildConfig.API_BASE_URL)
        val unique = UUID.randomUUID().toString()
        api.authenticate("auth/signup", mapOf(
            "email" to "photo-$unique@example.com", "password" to UUID.randomUUID().toString(),
            "nickname" to "photo-${unique.take(16)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "HABIT", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002")
        ))
        val ids = listOf(Color.GREEN, Color.BLUE).mapIndexed { index, color ->
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            val bytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
            bitmap.recycle()
            api.uploadImage("COMMUNITY_POST", "synthetic-$index.png", "image/png", bytes).get("mediaId").asString
        }
        val social = ServerSocialApi(api)
        val created = social.createPost("Synthetic photo integration", false, ids)
        val id = created.text("postId")
        val reloaded = social.post(id)
        val urls = reloaded.getAsJsonArray("mediaUrls").map { it.asString }
        assertEquals(2, urls.size)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(ids, urls.map { social.communityMediaId(context, it) })
        urls.forEach { assertTrue(social.imageBytes(it).isNotEmpty()) }
        val updated = social.updatePost(id, "One synthetic photo", false, reloaded.get("version").asLong, ids.takeLast(1))
        assertEquals(1, social.post(id).getAsJsonArray("mediaUrls").size())
        social.updatePost(id, "Attachments removed", false, updated.get("version").asLong, emptyList())
        assertEquals(0, social.post(id).getAsJsonArray("mediaUrls").size())
        val second = social.createPost("Pagination fixture two", false).text("postId")
        val third = social.createPost("Pagination fixture three", false).text("postId")
        val firstPage = api.request("GET", "community/posts?limit=2").asJsonObject
        assertTrue(firstPage.get("hasNext").asBoolean)
        val cursor = java.net.URLEncoder.encode(firstPage.text("nextCursor"), "UTF-8")
        val secondPage = api.request("GET", "community/posts?limit=2&cursor=$cursor").asJsonObject
        val seen = (firstPage.rows("items") + secondPage.rows("items")).map { it.text("postId") }
        assertTrue("Cursor must not skip the third newest post", seen.containsAll(listOf(id, second, third)))
        assertEquals("Lookahead must not duplicate a post", seen.size, seen.distinct().size)
    }
}

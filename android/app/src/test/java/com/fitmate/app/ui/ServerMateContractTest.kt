package com.fitmate.app.ui

import com.fitmate.app.data.model.Sport
import com.fitmate.app.data.remote.toMatePost
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerMateContractTest {
    @Test
    fun `server mate post preserves UUID while adapting the existing list card model`() {
        val post = toMatePost(JsonParser.parseString("""
            {
              "id":"a11b22c3-d44e-55f6-a77b-88c99d00e111",
              "title":"오늘 저녁 러닝 메이트",
              "sportCode":"RUNNING",
              "scheduledStartAt":"2026-09-08T12:30:00Z",
              "durationSeconds":2400,
              "locationSummary":"성수동 서울숲",
              "capacity":4,
              "acceptedCount":2,
              "difficulty":"BEGINNER",
              "body":"천천히 5km 달려요.",
              "estimatedCostKrw":0,
              "author":{"nickname":"러너"}
            }
        """).asJsonObject)

        assertEquals("a11b22c3-d44e-55f6-a77b-88c99d00e111", post.serverId)
        assertEquals(Sport.RUNNING, post.sport)
        assertEquals("약 40분", post.duration)
        assertEquals("초보", post.difficulty)
        assertEquals("성수동 서울숲", post.details.location)
        assertFalse(post.id < 0)
    }
}

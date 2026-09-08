package com.fitmate.app.ui

import com.fitmate.app.data.model.Sport
import com.fitmate.app.data.remote.toMatePost
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import com.fitmate.app.data.model.MateDetails
import com.fitmate.app.data.model.RecruitmentDraft
import com.fitmate.app.data.remote.mateCreateBody
import java.time.LocalDate
import java.time.ZoneId

class ServerMateContractTest {
    @Test fun `draft preserves extra fields and converts Korean local time to UTC`() {
        val draft = RecruitmentDraft("모집", Sport.RUNNING, "내일 19:30", "1시간 30분", 4, "초중급", "함께 달려요", false,
            MateDetails("공원", "2번 출입구", "3000", "맞팔 공개", mapOf("코스" to "공원")))
        val body = mateCreateBody(draft, "10000000-0000-0000-0000-000000000001", LocalDate.of(2026, 9, 9), ZoneId.of("Asia/Seoul"))
        assertEquals("2026-09-10T10:30:00Z", body["scheduledStartAt"])
        assertEquals(5400, body["durationSeconds"])
        assertEquals("MUTUAL", body["visibility"])
        assertEquals("2번 출입구", body["meetingPoint"])
        assertEquals(mapOf("코스" to "공원"), body["sportFields"])
        assertEquals(false, body["beginnerWelcome"])
    }

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

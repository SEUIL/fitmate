package com.fitmate.app.ui

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PostTimeTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test fun `formats UTC with device timezone and omits fractional seconds`() {
        assertEquals("2026-09-09 15:08", formatPostTime("2026-09-09T06:08:59.123456Z", seoul))
    }

    @Test fun `handles date and calendar year rollover`() {
        assertEquals("2027-01-01 01:05", formatPostTime("2026-12-31T16:05:00Z", seoul))
    }

    @Test fun `respects explicit offset and timezone`() {
        assertEquals("2026-09-09 06:08", formatPostTime("2026-09-09T15:08:00+09:00", ZoneId.of("UTC")))
    }

    @Test fun `preserves already formatted and legacy values`() {
        listOf("2026-09-09 15:08", "10분 전", "").forEach { assertEquals(it, formatPostTime(it, seoul)) }
    }
}

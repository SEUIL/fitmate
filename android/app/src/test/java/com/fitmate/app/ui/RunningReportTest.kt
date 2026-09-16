package com.fitmate.app.ui

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningReportTest {
    @Test fun `full report presents heart changes Samsung and GPS pace without coordinate values`() {
        val payload = JsonParser.parseString("""{
          "available":true,"analysisVersion":"external-timeseries-v1",
          "limitations":["PACE_PARTIAL_SAMPLES"],
          "heartRate":{"validSampleCount":5,"coveragePercent":92,"longGapCount":0,"maxGapSeconds":20,
            "peakBpm":172,"peakOffsetSeconds":80,"peakSustained":{"startOffsetSeconds":70,"endOffsetSeconds":90,"sampleCount":3},
            "biggestRise":{"startOffsetSeconds":10,"endOffsetSeconds":50,"changeBpm":35},
            "biggestFall":{"startOffsetSeconds":90,"endOffsetSeconds":140,"changeBpm":-28}},
          "pace":{"samsungSampleCount":4,"smartphoneGpsSampleCount":2,"samsungSourceUsed":true,"smartphoneGpsSourceUsed":true,
            "samsungFastestPaceSecondsPerKm":300,"smartphoneGpsFastestPaceSecondsPerKm":315}
        }""").asJsonObject

        val result = runningAnalysisPresentation(payload, totalSampleCount = 5)

        assertTrue(result.available)
        assertTrue(result.heartRateLines.any { it.contains("최고 심박") })
        assertTrue(result.heartRateLines.any { it.contains("상승 구간") })
        assertTrue(result.heartRateLines.any { it.contains("하강 구간") })
        assertTrue(result.heartRateLines.any { it.contains("지속 구간") })
        assertTrue(result.paceLines.any { it.contains("Samsung Health + 스마트폰 GPS") })
        assertTrue(result.paceLines.any { it.contains("일부 Samsung 샘플") })
        assertEquals("external-timeseries-v1", result.analysisVersion)
        assertFalse((result.heartRateLines + result.paceLines).joinToString().contains("37.123456"))
    }

    @Test fun `basic record and unavailable analysis stay explicit`() {
        val unavailable = JsonParser.parseString("""{
          "available":false,"analysisVersion":"external-timeseries-v1","limitations":["TIME_SERIES_NOT_CONNECTED"],
          "heartRate":{"validSampleCount":0,"coveragePercent":0,"longGapCount":0,"maxGapSeconds":0},
          "pace":{"samsungSampleCount":0,"smartphoneGpsSampleCount":0,"samsungSourceUsed":false,"smartphoneGpsSourceUsed":false}
        }""").asJsonObject

        val result = runningAnalysisPresentation(unavailable)

        assertFalse(result.available)
        assertEquals(null, result.analysisVersion)
        assertTrue(result.limitations.single().contains("연결되지"))
        assertTrue(result.paceLines.any { it.contains("사용 가능한 데이터 없음") })
    }

    @Test fun `zero one and multiple route points have separate private shapes`() {
        assertEquals(RoutePreviewKind.EMPTY, routePreview(emptyList()).kind)
        assertEquals(RoutePreviewKind.SINGLE_POINT, routePreview(listOf(37.123456 to 127.654321)).kind)
        val path = routePreview(listOf(37.1 to 127.1, 37.2 to 127.15, 37.25 to 127.3))
        assertEquals(RoutePreviewKind.PATH, path.kind)
        assertEquals(3, path.points.size)
        assertTrue(path.points.all { it.x in 0f..1f && it.y in 0f..1f })
        assertFalse(path.toString().contains("127.15"))
    }

    @Test fun `heart only partial speed long gaps and impossible analysis remain distinct`() {
        val heartOnly = JsonParser.parseString("""{
          "available":true,"limitations":["PACE_DATA_UNAVAILABLE","HEART_RATE_LONG_GAPS"],
          "heartRate":{"validSampleCount":4,"coveragePercent":40,"longGapCount":1,"maxGapSeconds":90,"peakBpm":150},
          "pace":{"samsungSampleCount":0,"smartphoneGpsSampleCount":0,"samsungSourceUsed":false,"smartphoneGpsSourceUsed":false}
        }""").asJsonObject
        val partialSpeed = JsonParser.parseString("""{
          "available":true,"limitations":["PACE_PARTIAL_SAMPLES"],
          "heartRate":{"validSampleCount":5,"coveragePercent":90,"longGapCount":0,"maxGapSeconds":10},
          "pace":{"samsungSampleCount":2,"smartphoneGpsSampleCount":0,"samsungSourceUsed":true,"smartphoneGpsSourceUsed":false}
        }""").asJsonObject

        val first = runningAnalysisPresentation(heartOnly, 4)
        val second = runningAnalysisPresentation(partialSpeed, 5)
        val failed = runningAnalysisPresentation(null)

        assertTrue(first.limitations.any { it.contains("긴 공백") })
        assertTrue(first.limitations.any { it.contains("페이스 데이터") })
        assertTrue(second.paceLines.any { it.contains("일부 Samsung 샘플") })
        assertFalse(failed.available)
        assertEquals(null, failed.analysisVersion)
        assertTrue(failed.limitations.single().contains("불러오지"))
    }
}

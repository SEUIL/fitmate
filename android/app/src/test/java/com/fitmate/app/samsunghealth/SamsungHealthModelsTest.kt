package com.fitmate.app.samsunghealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.samsung.android.sdk.health.data.error.ErrorCode
import com.samsung.android.sdk.health.data.error.HealthDataException

class SamsungHealthModelsTest {
    @Test fun `converts distance and speed`() {
        assertEquals(1.5f, SamsungWorkoutMetrics.kilometers(1_500f)!!, 0.001f)
        assertEquals(18f, SamsungWorkoutMetrics.kilometersPerHour(5f)!!, 0.001f)
    }
    @Test fun `calculates pace only with distance`() {
        assertEquals(300.0, SamsungWorkoutMetrics.paceSecondsPerKm(2_000f, 600)!!, 0.001)
        assertNull(SamsungWorkoutMetrics.paceSecondsPerKm(0f, 600))
    }
    @Test fun `masks device id`() { assertEquals("ab••••yz", SamsungDeviceFormatter.maskId("abcdefyz")) }
    @Test fun `identifies fit2 only from device metadata`() {
        assertEquals("Galaxy Fit2 추정", SamsungDeviceFormatter.label("ACCESSORY", "SM-R220", "Galaxy Fit2"))
        assertEquals("기기 확인 불가", SamsungDeviceFormatter.label(null, null, null))
    }
    @Test fun `maps known Samsung Health platform error`() {
        val result = SamsungHealthErrorMapper.map(HealthDataException(ErrorCode.ERR_PLATFORM_NOT_INSTALLED, ""))
        assertEquals(SamsungHealthStatus.PLATFORM_ERROR, result.first)
    }
}

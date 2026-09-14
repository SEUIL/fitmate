package com.fitmate.app.ui

import com.fitmate.app.samsunghealth.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SamsungHealthPresentationTest {
    @Test fun missingMetricIsNotZero() {
        assertEquals("기록 없음", samsungMetric(null, "bpm"))
        assertEquals("0 kcal", samsungMetric(BigDecimal.ZERO, "kcal"))
        assertEquals("103 bpm", samsungMetric(BigDecimal("103.00"), "bpm"))
    }
    @Test fun readyWithoutExercisePermissionIsNotConnected() {
        assertEquals("권한 필요", samsungConnectionLabel(SamsungHealthUiState(status = SamsungHealthStatus.READY)))
        assertEquals("사용 가능", samsungConnectionLabel(SamsungHealthUiState(status = SamsungHealthStatus.DATA_EMPTY,
            permissions = listOf(SamsungPermissionStatus("운동 읽기", true)))))
        assertEquals("확인 중", samsungConnectionLabel(SamsungHealthUiState()))
    }
    @Test fun shortWorkoutDoesNotBecomeZeroMinutes() {
        assertEquals("45초", samsungDuration(45))
        assertEquals("1분 34초", samsungDuration(94))
        assertEquals("22분", samsungDuration(1320))
    }
}

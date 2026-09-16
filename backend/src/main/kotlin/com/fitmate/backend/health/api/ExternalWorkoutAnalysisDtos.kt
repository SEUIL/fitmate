package com.fitmate.backend.health.api

import java.math.BigDecimal

data class ExternalWorkoutAnalysisResponse(
    val analysisVersion: String,
    val available: Boolean,
    val limitations: List<String>,
    val heartRate: HeartRateAnalysisResponse,
    val pace: PaceAnalysisResponse,
    val alignment: TimeSeriesAlignmentResponse,
)

data class HeartRateAnalysisResponse(
    val validSampleCount: Int,
    val coveragePercent: Int,
    val longGapCount: Int,
    val maxGapSeconds: Long,
    val peakBpm: BigDecimal? = null,
    val peakOffsetSeconds: Long? = null,
    val peakSustained: TimeOffsetSegmentResponse? = null,
    val biggestRise: HeartRateChangeResponse? = null,
    val biggestFall: HeartRateChangeResponse? = null,
    val unavailableReason: String? = null,
)

data class PaceAnalysisResponse(
    val samsungSampleCount: Int,
    val smartphoneGpsSampleCount: Int,
    val samsungSourceUsed: Boolean,
    val smartphoneGpsSourceUsed: Boolean,
    val samsungFastestPaceSecondsPerKm: Int? = null,
    val smartphoneGpsFastestPaceSecondsPerKm: Int? = null,
    val unavailableReason: String? = null,
)

data class TimeOffsetSegmentResponse(val startOffsetSeconds: Long, val endOffsetSeconds: Long, val sampleCount: Int)
data class HeartRateChangeResponse(val startOffsetSeconds: Long, val endOffsetSeconds: Long, val changeBpm: BigDecimal)
data class TimeSeriesAlignmentResponse(val alignedPairCount: Int, val matchingWindowSeconds: Long)

package com.fitmate.app.running

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** App-private route state. Coordinates are retained only when route recording is enabled. */
internal data class RunRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val recordedAtEpochMillis: Long,
    val altitudeMeters: Double? = null,
)

internal data class RunLocationSample(
    val latitude: Double,
    val longitude: Double,
    val recordedAtEpochMillis: Long,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
)

internal data class AcceptedRunLocation(
    val distanceMeters: Double,
    val routePoint: RunRoutePoint?,
)

/**
 * Filters the same location stream used for distance measurement. It never retains a route point
 * when route recording is disabled, and a resumed segment deliberately starts with a new anchor.
 */
internal class RunRouteCollector(
    private val recordRoute: Boolean,
    restoredPoints: List<RunRoutePoint> = emptyList(),
) {
    private val points = restoredPoints.toMutableList()
    private var lastTimestamp = restoredPoints.maxOfOrNull { it.recordedAtEpochMillis } ?: Long.MIN_VALUE
    private var segmentAnchor: RunLocationSample? = null

    fun accept(sample: RunLocationSample): AcceptedRunLocation? {
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite() ||
            sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0 ||
            !sample.accuracyMeters.isFinite() || sample.accuracyMeters < 0f || sample.accuracyMeters > MAX_ACCURACY_METERS ||
            sample.recordedAtEpochMillis <= lastTimestamp
        ) return null

        val previous = segmentAnchor
        val distance = previous?.let { haversineMeters(it.latitude, it.longitude, sample.latitude, sample.longitude) } ?: 0.0
        val elapsedSeconds = previous?.let { (sample.recordedAtEpochMillis - it.recordedAtEpochMillis) / 1_000.0 } ?: 0.0
        if (previous != null && (elapsedSeconds <= 0.0 || distance / elapsedSeconds > MAX_SPEED_METERS_PER_SECOND)) return null

        lastTimestamp = sample.recordedAtEpochMillis
        segmentAnchor = sample
        val routePoint = if (recordRoute) RunRoutePoint(
            latitude = sample.latitude,
            longitude = sample.longitude,
            recordedAtEpochMillis = sample.recordedAtEpochMillis,
            altitudeMeters = sample.altitudeMeters?.takeIf(Double::isFinite),
        ).also(points::add) else null
        return AcceptedRunLocation(distanceMeters = distance, routePoint = routePoint)
    }

    /** A paused interval must not be bridged to the resumed location. */
    fun pauseSegment() {
        segmentAnchor = null
    }

    fun routePoints(): List<RunRoutePoint> = points.toList()

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latitudeDelta = Math.toRadians(lat2 - lat1)
        val longitudeDelta = Math.toRadians(lon2 - lon1)
        val a = sin(latitudeDelta / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(longitudeDelta / 2).pow(2)
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_SPEED_METERS_PER_SECOND = 8.5
    }
}

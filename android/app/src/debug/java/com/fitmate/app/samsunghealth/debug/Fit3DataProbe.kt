package com.fitmate.app.samsunghealth.debug

import android.app.Activity
import android.content.Context
import com.fitmate.app.samsunghealth.SamsungHealthErrorMapper
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

internal sealed interface Fit3ProbeResult {
    data class Success(val report: Fit3ProbeReport) : Fit3ProbeResult
    data object PermissionRequired : Fit3ProbeResult
    data object NoRecentRunning : Fit3ProbeResult
    data class Failure(val message: String, val canResolve: Boolean) : Fit3ProbeResult
}

internal class Fit3DataProbe(context: Context) {
    private val appContext = context.applicationContext
    private val exercisePermission = Permission.of(DataTypes.EXERCISE, AccessType.READ)
    private var pendingResolution: ResolvablePlatformException? = null

    suspend fun run(): Fit3ProbeResult = try {
        val store = HealthDataService.getStore(appContext)
        if (exercisePermission !in store.getGrantedPermissions(setOf(exercisePermission))) {
            Fit3ProbeResult.PermissionRequired
        } else {
            val first = readLatestRunning(store)
            val second = readLatestRunning(store)
            if (first == null || second == null) Fit3ProbeResult.NoRecentRunning
            else Fit3ProbeResult.Success(Fit3DataProbeAnalyzer.analyze(first, second))
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (error is ResolvablePlatformException && error.hasResolution) pendingResolution = error
        Fit3ProbeResult.Failure(
            message = SamsungHealthErrorMapper.map(error).second,
            canResolve = SamsungHealthErrorMapper.isResolvable(error),
        )
    }

    suspend fun requestPermission(activity: Activity): Fit3ProbeResult = try {
        HealthDataService.getStore(appContext).requestPermissions(setOf(exercisePermission), activity)
        run()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (error is ResolvablePlatformException && error.hasResolution) pendingResolution = error
        Fit3ProbeResult.Failure(
            message = SamsungHealthErrorMapper.map(error).second,
            canResolve = SamsungHealthErrorMapper.isResolvable(error),
        )
    }

    fun resolve(activity: Activity) {
        pendingResolution?.resolve(activity)
    }

    private suspend fun readLatestRunning(store: HealthDataStore): ProbeRunningRecord? {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val filter = LocalTimeFilter.of(now.minusDays(14), now)
        val points = buildList {
            var pageToken: String? = null
            val seenPageTokens = mutableSetOf<String>()
            do {
                val builder = DataTypes.EXERCISE.readDataRequestBuilder
                    .setLocalTimeFilter(filter)
                    .setPageSize(200)
                pageToken?.let(builder::setPageToken)
                val response = store.readData(builder.build())
                addAll(response.dataList)
                pageToken = response.pageToken?.takeIf { it.isNotBlank() && seenPageTokens.add(it) }
            } while (!pageToken.isNullOrBlank())
        }
        return points.flatMap { point ->
            val sessions = point.getValue(DataType.ExerciseType.SESSIONS).orEmpty()
            val metadataMatch = point.dataSource?.deviceId?.let { deviceId ->
                runCatching { store.getDeviceManager().getDevice(deviceId) }.getOrNull()
            }.toFit3MetadataMatch()
            sessions.mapNotNull { session ->
                if (session.exerciseType?.toString() != "RUNNING") null
                else session.toProbeRecord(
                    sourceRecordId = point.uid,
                    sourceModifiedAt = point.updateTime,
                    sessionCount = sessions.size,
                    fit3MetadataMatch = metadataMatch,
                )
            }
        }.maxByOrNull { it.startedAt }
    }

    private fun ExerciseSession.toProbeRecord(
        sourceRecordId: String,
        sourceModifiedAt: Instant?,
        sessionCount: Int,
        fit3MetadataMatch: Fit3MetadataMatch,
    ) = ProbeRunningRecord(
        sourceRecordId = sourceRecordId,
        exerciseType = exerciseType?.toString() ?: "기록 없음",
        startedAt = startTime,
        endedAt = endTime,
        sourceModifiedAt = sourceModifiedAt,
        activeDurationSeconds = duration.seconds,
        exerciseSessionCount = sessionCount,
        samples = log.orEmpty().map { value ->
            ProbeLogSample(
                timestamp = value.timestamp,
                heartRate = value.heartRate,
                speedMetersPerSecond = value.speed,
            )
        },
        fit3MetadataMatch = fit3MetadataMatch,
    )

    private fun com.samsung.android.sdk.health.data.device.Device?.toFit3MetadataMatch(): Fit3MetadataMatch {
        this ?: return Fit3MetadataMatch.UNAVAILABLE
        val value = listOfNotNull(model, name).joinToString(" ").uppercase().replace("-", "").replace(" ", "")
        return if ("FIT3" in value || "SMR390" in value) Fit3MetadataMatch.MATCH else Fit3MetadataMatch.NOT_MATCH
    }
}

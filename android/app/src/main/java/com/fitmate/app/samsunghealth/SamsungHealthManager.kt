package com.fitmate.app.samsunghealth

import android.app.Activity
import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class SamsungHealthManager(context: Context) {
    private val appContext = context.applicationContext
    private var pendingResolution: ResolvablePlatformException? = null
    private val requestedPermissions = linkedMapOf(
        "걸음 수 읽기" to Permission.of(DataTypes.STEPS, AccessType.READ),
        "운동 읽기" to Permission.of(DataTypes.EXERCISE, AccessType.READ)
    )

    suspend fun refresh(): SamsungHealthUiState = try {
        val store = store()
        val granted = store.getGrantedPermissions(requestedPermissions.values.toSet())
        SamsungHealthUiState(
            status = SamsungHealthStatus.READY,
            permissions = requestedPermissions.map { SamsungPermissionStatus(it.key, it.value in granted) }
        )
    } catch (error: Throwable) { stateForError(error) }

    suspend fun requestPermissions(activity: Activity): SamsungHealthUiState = try {
        val store = store()
        store.requestPermissions(requestedPermissions.values.toSet(), activity)
        refresh()
    } catch (error: Throwable) { stateForError(error) }

    suspend fun requestExercisePermission(activity: Activity): SamsungHealthUiState = try {
        val store = store()
        store.requestPermissions(setOf(requireNotNull(requestedPermissions["운동 읽기"])), activity)
        refresh()
    } catch (error: Throwable) { stateForError(error) }

    suspend fun loadWorkouts(startedAt: Instant, endedAt: Instant): SamsungHealthUiState {
        val base = refresh()
        if (base.status != SamsungHealthStatus.READY) return base
        if (base.permissions.none { it.label == "운동 읽기" && it.granted }) {
            return base.copy(status = SamsungHealthStatus.PERMISSION_MISSING, message = "Samsung Health 운동 읽기 권한을 허용하세요.")
        }
        return try {
            val workouts = readWorkouts(store(), startedAt.minusSeconds(600), endedAt.plusSeconds(600))
            base.copy(
                status = if (workouts.isEmpty()) SamsungHealthStatus.DATA_EMPTY else SamsungHealthStatus.READY,
                message = if (workouts.isEmpty()) "선택한 세션과 같은 시간대의 운동 기록이 없습니다." else null,
                workouts = workouts,
                lastLoadedAtMillis = System.currentTimeMillis(),
            )
        } catch (error: Throwable) { stateForError(error, base.permissions) }
    }

    suspend fun load(): SamsungHealthUiState {
        val base = refresh()
        if (base.status != SamsungHealthStatus.READY) return base
        if (base.permissions.none { it.granted }) return base.copy(status = SamsungHealthStatus.PERMISSION_MISSING, message = "Samsung Health 읽기 권한을 허용하세요.")
        return try {
            val store = store()
            val granted = store.getGrantedPermissions(requestedPermissions.values.toSet())
            val start = LocalDate.now().atStartOfDay()
            val end = LocalDateTime.now()
            val steps = if (requestedPermissions["걸음 수 읽기"] in granted) {
                store.aggregateData(
                    DataType.StepsType.TOTAL.requestBuilder.setLocalTimeFilter(LocalTimeFilter.of(start, end)).build()
                ).dataList.firstOrNull()?.value
            } else null
            val workouts = if (requestedPermissions["운동 읽기"] in granted) readWorkouts(
                store,
                LocalDateTime.now().minusDays(7).atZone(ZoneId.systemDefault()).toInstant(),
                LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(),
            ) else emptyList()
            base.copy(
                status = if (workouts.isEmpty() && steps == null) SamsungHealthStatus.DATA_EMPTY else SamsungHealthStatus.READY,
                message = if (workouts.isEmpty() && steps == null) "Samsung Health에 조회 가능한 데이터가 없습니다." else null,
                todaySteps = steps,
                stepRange = "$start ~ $end",
                workouts = workouts,
                lastLoadedAtMillis = System.currentTimeMillis()
            )
        } catch (error: Throwable) { stateForError(error, base.permissions) }
    }

    fun resolve(activity: Activity) { pendingResolution?.resolve(activity) }

    private suspend fun readWorkouts(store: HealthDataStore, start: Instant, end: Instant): List<SamsungWorkoutUiModel> {
        val zone = ZoneId.systemDefault()
        val filter = LocalTimeFilter.of(LocalDateTime.ofInstant(start, zone), LocalDateTime.ofInstant(end, zone))
        val dataPoints = buildList {
            var pageToken: String? = null
            val seenPageTokens = mutableSetOf<String>()
            do {
                val builder = DataTypes.EXERCISE.readDataRequestBuilder.setLocalTimeFilter(filter).setPageSize(200)
                pageToken?.let(builder::setPageToken)
                val response = store.readData(builder.build())
                addAll(response.dataList)
                pageToken = response.pageToken?.takeIf { it.isNotBlank() && seenPageTokens.add(it) }
            } while (!pageToken.isNullOrBlank())
        }
        return dataPoints.flatMap { point ->
            val source = point.dataSource
            val device = source?.deviceId?.let { id -> store.getDeviceManager().getDevice(id)?.toUiModel() }
            val sessions = point.getValue(DataType.ExerciseType.SESSIONS).orEmpty()
            sessions.map { session -> point.toWorkout(session, device, sessions.size) }
        }
    }

    private fun com.samsung.android.sdk.health.data.data.HealthDataPoint.toWorkout(
        session: ExerciseSession,
        device: SamsungDeviceUiModel?,
        sessionCount: Int,
    ): SamsungWorkoutUiModel {
        val timeSeries = sanitizeSamsungTimeSeriesWithQuality(
            session.log.orEmpty().map { value ->
                SamsungWorkoutTimeSample(value.timestamp, value.heartRate, value.speed)
            },
            session.startTime,
            session.endTime,
        )
        return SamsungWorkoutUiModel(
            uid = uid, sourceAppId = dataSource?.appId, sourceModifiedAt = updateTime, device = device,
            exerciseType = session.exerciseType?.toString() ?: "기록 없음", customTitle = session.customTitle,
            startTime = session.startTime, endTime = session.endTime,
            activeDurationSeconds = session.duration.seconds, sessionCount = sessionCount, distanceMeters = session.distance,
            calories = session.calories.takeIf { it != 0f }, meanHeartRate = session.meanHeartRate,
            minHeartRate = session.minHeartRate, maxHeartRate = session.maxHeartRate,
            meanSpeedMetersPerSecond = session.meanSpeed, maxSpeedMetersPerSecond = session.maxSpeed,
            altitudeGainMeters = session.altitudeGain, altitudeLossMeters = session.altitudeLoss,
            meanCadence = session.meanCadence, vo2Max = session.vo2Max, logSampleCount = session.log?.size ?: 0,
            timeSeries = timeSeries.samples,
            timeSeriesQuality = timeSeries.quality,
        )
    }

    private fun com.samsung.android.sdk.health.data.device.Device.toUiModel(): SamsungDeviceUiModel = SamsungDeviceUiModel(
        maskedId = SamsungDeviceFormatter.maskId(id), type = deviceType.toString(), manufacturer = manufacturer,
        model = model, name = name, label = SamsungDeviceFormatter.label(deviceType.toString(), model, name)
    )

    private fun store() = HealthDataService.getStore(appContext)

    private fun stateForError(error: Throwable, permissions: List<SamsungPermissionStatus> = emptyList()): SamsungHealthUiState {
        if (error is CancellationException) throw error
        if (error is ResolvablePlatformException && error.hasResolution) pendingResolution = error
        val (status, message) = SamsungHealthErrorMapper.map(error)
        return SamsungHealthUiState(status, message, SamsungHealthErrorMapper.isResolvable(error), permissions)
    }
}

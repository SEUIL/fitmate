package com.fitmate.app.running

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

internal data class RunTrackingUpdate(
    val active: Boolean = false,
    val finished: Boolean = false,
    val paused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val activeDurationSeconds: Int = 0,
    val meters: Int = 0,
    val routePointCount: Int = 0,
    val routePoints: List<RunRoutePoint> = emptyList(),
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val currentSpeedMps: Float? = null,
    val accuracyMeters: Float? = null,
    val message: String? = null,
)

internal data class RunTrackingCheckpoint(
    val startedAtEpochMillis: Long,
    val activeBeforePauseMillis: Long,
    val activeSegmentStartedElapsedMillis: Long,
    val paused: Boolean,
    val finished: Boolean,
    val completedAtEpochMillis: Long? = null,
    val meters: Double = 0.0,
    val recordRoute: Boolean = false,
    val routePoints: List<RunRoutePoint> = emptyList(),
    val message: String? = null,
) {
    fun activeMillis(nowElapsedMillis: Long): Long = activeBeforePauseMillis +
        if (paused || finished) 0L else (nowElapsedMillis - activeSegmentStartedElapsedMillis).coerceAtLeast(0L)

    fun update(nowWallMillis: Long, nowElapsedMillis: Long, speed: Float? = null, accuracy: Float? = null) = RunTrackingUpdate(
        active = !finished,
        finished = finished,
        paused = paused,
        elapsedSeconds = (((if (finished) requireNotNull(completedAtEpochMillis) else nowWallMillis) - startedAtEpochMillis).coerceAtLeast(0L) / 1_000L).toInt(),
        activeDurationSeconds = (activeMillis(nowElapsedMillis) / 1_000L).toInt(),
        meters = meters.toInt(),
        routePointCount = routePoints.size,
        routePoints = routePoints,
        startedAtEpochMillis = startedAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        currentSpeedMps = speed,
        accuracyMeters = accuracy,
        message = message,
    )
}

internal object RunTrackingStore {
    private val mutable = MutableStateFlow(RunTrackingUpdate())
    val updates = mutable.asStateFlow()
    internal fun publish(value: RunTrackingUpdate) { mutable.value = value }
}

/** Foreground-only location tracker. It never requests background location permission. */
class RunTrackingService : Service(), LocationListener {
    companion object {
        private const val CHANNEL_ID = "fitmate_running"
        private const val NOTIFICATION_ID = 7101
        private const val ACTION_START = "com.fitmate.app.running.START"
        private const val ACTION_PAUSE = "com.fitmate.app.running.PAUSE"
        private const val ACTION_RESUME = "com.fitmate.app.running.RESUME"
        private const val ACTION_STOP = "com.fitmate.app.running.STOP"
        private const val EXTRA_STARTED_AT = "startedAtEpochMillis"
        private const val EXTRA_RECORD_ROUTE = "recordRoute"
        private const val CHECKPOINT_PREFS = "fitmate_running_checkpoint"
        private const val CHECKPOINT_KEY = "state"

        fun start(context: Context, startedAtEpochMillis: Long, recordRoute: Boolean) = dispatch(context, ACTION_START) {
            putExtra(EXTRA_STARTED_AT, startedAtEpochMillis)
            putExtra(EXTRA_RECORD_ROUTE, recordRoute)
        }
        fun pause(context: Context) = dispatch(context, ACTION_PAUSE)
        fun resume(context: Context) = dispatch(context, ACTION_RESUME)
        fun stop(context: Context) = dispatch(context, ACTION_STOP)

        internal fun restore(context: Context) {
            readCheckpoint(context)?.let { checkpoint ->
                RunTrackingStore.publish(checkpoint.update(System.currentTimeMillis(), SystemClock.elapsedRealtime()))
            }
        }

        internal fun clearFinished(context: Context) {
            if (readCheckpoint(context)?.finished == true) {
                preferences(context).edit().remove(CHECKPOINT_KEY).commit()
                RunTrackingStore.publish(RunTrackingUpdate())
            }
        }

        internal fun discard(context: Context) {
            context.stopService(Intent(context, RunTrackingService::class.java))
            preferences(context).edit().remove(CHECKPOINT_KEY).commit()
            RunTrackingStore.publish(RunTrackingUpdate())
        }

        private fun dispatch(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(action).apply(extras)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        private fun preferences(context: Context) = context.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
        private fun readCheckpoint(context: Context): RunTrackingCheckpoint? = preferences(context)
            .getString(CHECKPOINT_KEY, null)?.let { value -> runCatching { Gson().fromJson(value, RunTrackingCheckpoint::class.java) }.getOrNull() }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private var checkpoint: RunTrackingCheckpoint? = null
    private var collector = RunRouteCollector(false)
    private var currentSpeed: Float? = null
    private var currentAccuracy: Float? = null
    private var ticking = false
    private val ticker = object : Runnable {
        override fun run() {
            publish()
            if (ticking) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(intent)
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
            null -> restoreTracking()
        }
        return START_STICKY
    }

    private fun startTracking(intent: Intent) {
        val existing = checkpoint ?: readCheckpoint(this)
        if (existing?.finished == false) {
            checkpoint = existing
            restoreTracking()
            return
        }
        if (!hasLocationPermission()) {
            RunTrackingStore.publish(RunTrackingUpdate(message = "위치 권한이 없어 GPS 기록을 시작할 수 없어요."))
            stopSelf()
            return
        }
        val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis()).coerceAtMost(System.currentTimeMillis())
        checkpoint = RunTrackingCheckpoint(
            startedAtEpochMillis = startedAt,
            activeBeforePauseMillis = 0L,
            activeSegmentStartedElapsedMillis = SystemClock.elapsedRealtime(),
            paused = false,
            finished = false,
            recordRoute = intent.getBooleanExtra(EXTRA_RECORD_ROUTE, false),
        )
        collector = RunRouteCollector(requireNotNull(checkpoint).recordRoute)
        currentSpeed = null
        currentAccuracy = null
        startForegroundNotification()
        requestLocationUpdates()
        startTicker()
        publish()
    }

    private fun restoreTracking() {
        val restored = checkpoint ?: readCheckpoint(this) ?: return
        checkpoint = restored
        collector = RunRouteCollector(restored.recordRoute, restored.routePoints).also { it.pauseSegment() }
        if (restored.finished) {
            RunTrackingStore.publish(restored.update(System.currentTimeMillis(), SystemClock.elapsedRealtime()))
            stopSelf()
            return
        }
        startForegroundNotification()
        if (!restored.paused) requestLocationUpdates()
        startTicker()
        publish()
    }

    private fun pauseTracking() {
        val current = checkpoint ?: return
        if (!current.paused && !current.finished) {
            checkpoint = current.copy(
                activeBeforePauseMillis = current.activeMillis(SystemClock.elapsedRealtime()),
                activeSegmentStartedElapsedMillis = 0L,
                paused = true,
            )
            collector.pauseSegment()
            removeLocationUpdates()
        }
        publish()
    }

    private fun resumeTracking() {
        val current = checkpoint ?: return
        if (current.finished) return
        if (!hasLocationPermission()) {
            checkpoint = current.copy(message = "위치 권한이 해제되어 GPS 좌표를 기록하지 못하고 있어요.")
            publish()
            return
        }
        if (current.paused) {
            checkpoint = current.copy(
                activeSegmentStartedElapsedMillis = SystemClock.elapsedRealtime(),
                paused = false,
                message = null,
            )
            collector.pauseSegment()
        }
        requestLocationUpdates()
        publish()
    }

    private fun stopTracking() {
        val current = checkpoint ?: return
        if (current.finished) return
        val activeMillis = current.activeMillis(SystemClock.elapsedRealtime())
        val completedAt = max(System.currentTimeMillis(), current.startedAtEpochMillis + activeMillis)
        checkpoint = current.copy(
            activeBeforePauseMillis = activeMillis,
            activeSegmentStartedElapsedMillis = 0L,
            paused = true,
            finished = true,
            completedAtEpochMillis = completedAt,
        )
        ticking = false
        handler.removeCallbacks(ticker)
        removeLocationUpdates()
        publish()
        removeForeground()
        stopSelf()
    }

    private fun startTicker() {
        ticking = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun requestLocationUpdates() {
        if (!hasLocationPermission()) {
            checkpoint = checkpoint?.copy(message = "위치 권한이 해제되어 GPS 좌표를 기록하지 못하고 있어요.")
            publish()
            return
        }
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            checkpoint = checkpoint?.copy(message = "위치 서비스를 켜고 다시 시도해 주세요.")
            publish()
            return
        }
        runCatching { locationManager.requestLocationUpdates(provider, 2_000L, 2f, this, Looper.getMainLooper()) }
            .onFailure {
                checkpoint = checkpoint?.copy(message = "GPS 위치를 가져오지 못했어요.")
                publish()
            }
    }

    private fun removeLocationUpdates() = runCatching { locationManager.removeUpdates(this) }

    override fun onLocationChanged(location: Location) {
        val current = checkpoint ?: return
        if (current.paused || current.finished) return
        if (!hasLocationPermission()) {
            checkpoint = current.copy(message = "위치 권한이 해제되어 GPS 좌표를 기록하지 못하고 있어요.")
            removeLocationUpdates()
            publish()
            return
        }
        currentAccuracy = location.accuracy
        currentSpeed = location.speed.takeIf { location.hasSpeed() }
        val recordedAt = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        if (recordedAt < current.startedAtEpochMillis) {
            checkpoint = current.copy(message = "GPS 시각을 확인하는 중이에요.")
            publish()
            return
        }
        val accepted = collector.accept(
            RunLocationSample(
                latitude = location.latitude,
                longitude = location.longitude,
                recordedAtEpochMillis = recordedAt,
                accuracyMeters = location.accuracy,
                altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            ),
        )
        if (accepted == null) {
            checkpoint = current.copy(message = "GPS 정확도 또는 이동 값을 확인하는 중이에요.")
            publish()
            return
        }
        checkpoint = current.copy(
            meters = current.meters + accepted.distanceMeters,
            routePoints = collector.routePoints(),
            message = null,
        )
        publish()
    }

    override fun onProviderDisabled(provider: String) {
        checkpoint = checkpoint?.copy(message = "GPS가 꺼져 있어 좌표를 기록하지 못하고 있어요.")
        publish()
    }

    override fun onProviderEnabled(provider: String) {
        checkpoint = checkpoint?.copy(message = null)
        publish()
    }

    private fun publish() {
        val current = checkpoint ?: return
        saveCheckpoint(current)
        val update = current.update(System.currentTimeMillis(), SystemClock.elapsedRealtime(), currentSpeed, currentAccuracy)
        RunTrackingStore.publish(update)
        if (!current.finished) updateNotification(update)
    }

    private fun saveCheckpoint(value: RunTrackingCheckpoint) {
        preferences(this).edit().putString(CHECKPOINT_KEY, Gson().toJson(value)).commit()
    }

    private fun startForegroundNotification() {
        val notification = notification("러닝 기록을 시작하는 중…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(update: RunTrackingUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(if (update.paused) "러닝 일시정지" else "${update.activeDurationSeconds / 60}분 · ${"%.2f".format(update.meters / 1000.0)} km"),
        )
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("FitMate 러닝 기록 중")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "러닝 기록", NotificationManager.IMPORTANCE_LOW).apply {
                description = "러닝 GPS 기록이 진행 중일 때 표시됩니다."
            },
        )
    }

    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ticking = false
        handler.removeCallbacks(ticker)
        removeLocationUpdates()
        super.onDestroy()
    }
}

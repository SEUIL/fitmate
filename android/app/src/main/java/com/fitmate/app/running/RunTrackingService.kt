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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RunTrackingUpdate(
    val active: Boolean = false,
    val paused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val meters: Int = 0,
    val currentSpeedMps: Float? = null,
    val accuracyMeters: Float? = null,
    val message: String? = null
)

object RunTrackingStore {
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

        fun start(context: Context) = dispatch(context, ACTION_START)
        fun pause(context: Context) = dispatch(context, ACTION_PAUSE)
        fun resume(context: Context) = dispatch(context, ACTION_RESUME)
        fun stop(context: Context) = context.stopService(Intent(context, RunTrackingService::class.java))

        private fun dispatch(context: Context, action: String) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var meters = 0.0
    private var startedAt = 0L
    private var elapsedBeforePause = 0L
    private var paused = false
    private var currentSpeed: Float? = null
    private var currentAccuracy: Float? = null
    private var message: String? = null
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
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        if (!hasLocationPermission()) {
            message = "위치 권한이 없어 GPS 기록을 시작할 수 없어요."
            RunTrackingStore.publish(RunTrackingUpdate(message = message))
            stopSelf()
            return
        }
        lastLocation = null; meters = 0.0; elapsedBeforePause = 0L; startedAt = SystemClock.elapsedRealtime()
        paused = false; message = null; ticking = true
        startForegroundNotification()
        requestLocationUpdates()
        handler.removeCallbacks(ticker); handler.post(ticker)
    }

    private fun pauseTracking() {
        if (!paused) elapsedBeforePause += SystemClock.elapsedRealtime() - startedAt
        paused = true; lastLocation = null
        removeLocationUpdates(); publish()
    }

    private fun resumeTracking() {
        if (!hasLocationPermission()) { message = "위치 권한이 해제되어 재개할 수 없어요."; publish(); return }
        if (paused) startedAt = SystemClock.elapsedRealtime()
        paused = false; lastLocation = null; message = null
        requestLocationUpdates(); publish()
    }

    private fun stopTracking() {
        ticking = false; handler.removeCallbacks(ticker); removeLocationUpdates()
        RunTrackingStore.publish(RunTrackingUpdate())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun requestLocationUpdates() {
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) { message = "위치 서비스를 켜고 다시 시도해 주세요."; publish(); return }
        runCatching { locationManager.requestLocationUpdates(provider, 2_000L, 2f, this, Looper.getMainLooper()) }
            .onFailure { message = "GPS 위치를 가져오지 못했어요."; publish() }
    }

    private fun removeLocationUpdates() = runCatching { locationManager.removeUpdates(this) }

    override fun onLocationChanged(location: Location) {
        if (paused) return
        currentAccuracy = location.accuracy; currentSpeed = location.speed.takeIf { location.hasSpeed() }
        if (location.accuracy > 50f) { message = "GPS 정확도를 기다리는 중이에요."; publish(); return }
        val previous = lastLocation
        if (previous != null) {
            val delta = previous.distanceTo(location)
            val elapsed = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
            val plausible = delta in 0.5f..100f && (elapsed <= 0 || delta / elapsed <= 8.5)
            if (plausible) meters += delta
        }
        lastLocation = location; message = null; publish()
    }

    override fun onProviderDisabled(provider: String) { message = "GPS가 꺼져 있어 위치를 기록할 수 없어요."; publish() }
    override fun onProviderEnabled(provider: String) { message = null; publish() }

    private fun elapsedSeconds(): Int = ((elapsedBeforePause + if (paused) 0L else SystemClock.elapsedRealtime() - startedAt) / 1_000L).toInt()
    private fun publish() {
        val update = RunTrackingUpdate(true, paused, elapsedSeconds(), meters.toInt(), currentSpeed, currentAccuracy, message)
        RunTrackingStore.publish(update)
        updateNotification(update)
    }

    private fun startForegroundNotification() {
        val notification = notification("러닝 기록을 시작하는 중…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification)
    }
    private fun updateNotification(update: RunTrackingUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,
            notification(if (update.paused) "러닝 일시정지" else "${update.elapsedSeconds / 60}분 · ${"%.2f".format(update.meters / 1000.0)} km"))
    }
    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("FitMate 러닝 기록 중")
        .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).build()
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "러닝 기록", NotificationManager.IMPORTANCE_LOW).apply { description = "러닝 GPS 기록이 진행 중일 때 표시됩니다." }
        )
    }
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        ticking = false; handler.removeCallbacks(ticker); removeLocationUpdates()
        RunTrackingStore.publish(RunTrackingUpdate())
        super.onDestroy()
    }
}

package com.example.dronepilottracking2026.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.dronepilottracking2026.DronePilotApplication
import com.example.dronepilottracking2026.R
import com.example.dronepilottracking2026.core.mqtt.MqttManager
import com.example.dronepilottracking2026.core.mqtt.MqttPayloadBuilder
import com.example.dronepilottracking2026.core.mqtt.batterySnapshot
import com.example.dronepilottracking2026.data.local.MqttConfigDataStore
import com.example.dronepilottracking2026.data.local.ProfileDataStore
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import com.example.dronepilottracking2026.data.model.LocationData
import com.example.dronepilottracking2026.data.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class LocationTrackingService : Service() {
    companion object {
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 3101
        private const val ACTION_START = "com.example.dronepilottracking2026.action.START_LOCATION"
        private const val ACTION_STOP = "com.example.dronepilottracking2026.action.STOP_LOCATION"
        private const val ACTION_SOS = "com.example.dronepilottracking2026.action.SOS"
        private const val ACTION_CLEAR_SOS = "com.example.dronepilottracking2026.action.CLEAR_SOS"
        private const val ACTION_SEND_AVATAR = "com.example.dronepilottracking2026.action.SEND_AVATAR"
        private const val DEFAULT_INTERVAL_MS = 5_000L

        private val _locationUpdates = MutableSharedFlow<Result<LocationData>>(
            replay = 1,
            extraBufferCapacity = 64
        )
        val locationUpdates = _locationUpdates.asSharedFlow()
        private val _lastLocation = MutableStateFlow<LocationData?>(null)
        val lastLocation = _lastLocation.asStateFlow()
        private val _sosResult = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 8)
        val sosResult = _sosResult.asSharedFlow()
        private val _sosActive = MutableStateFlow(false)
        val sosActive = _sosActive.asStateFlow()
        private val _avatarSendResult = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 8)
        val avatarSendResult = _avatarSendResult.asSharedFlow()

        fun requestSendAvatar(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_SEND_AVATAR)
            )
        }

        fun requestSos(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_SOS)
            )
        }

        fun clearSos(context: android.content.Context) {
            context.startService(Intent(context, LocationTrackingService::class.java).setAction(ACTION_CLEAR_SOS))
        }

        fun start(context: android.content.Context) {
            val intent = Intent(context, LocationTrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: LocationRepository
    private var locationJob: kotlinx.coroutines.Job? = null
    private var mqttConfigJob: kotlinx.coroutines.Job? = null
    private var mqttManager: MqttManager? = null
    private var mqttConfig: MqttConfig = MqttConfig()
    private var profile: PersonnelProfile? = null
    private lateinit var payloadBuilder: MqttPayloadBuilder
    private var locationIntervalMs = DEFAULT_INTERVAL_MS
    private var heartRateJob: kotlinx.coroutines.Job? = null
    private var lastHeartRate = com.example.dronepilottracking2026.core.bluetooth.BluetoothLeService.bpm.value
    private var lastHeartRatePublishAt = 0L

    override fun onCreate() {
        super.onCreate()
        repository = LocationRepository(applicationContext)
        payloadBuilder = MqttPayloadBuilder(applicationContext)
        createNotificationChannel()
        observeIncomingSos()
    }

    private fun observeIncomingSos() {
        mqttManager?.onSosMessageReceived = { _, payload ->
            runCatching {
                when (JSONObject(payload).optInt("sos", -1)) {
                    1 -> _sosActive.value = true
                    0 -> _sosActive.value = false
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_SOS -> {
                startTracking()
                sendSos()
            }
            ACTION_CLEAR_SOS -> {
                startTracking()
                clearSos()
            }
            ACTION_SEND_AVATAR -> {
                startTracking()
                sendAvatar()
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        startAsForeground()
        if (locationJob?.isActive == true) return
        mqttManager = (application as DronePilotApplication).mqttManager
        observeIncomingSos()
        observeMqttConfiguration()
        startLocationUpdates(locationIntervalMs)
        startHeartRateImmediatePublisher()
    }

    private fun startLocationUpdates(intervalMs: Long) {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            repository.locationFlow(intervalMs).collect { result ->
                _locationUpdates.emit(result)
                val currentLocation = result.getOrNull() ?: return@collect
                _lastLocation.value = currentLocation
                val currentProfile = profile ?: return@collect
                val config = mqttConfig
                if (!config.isComplete) return@collect
                val interval = locationIntervalMs
                val now = System.currentTimeMillis()
                if (now - lastPublishAt < interval) return@collect
                lastPublishAt = now
                val (batteryLevel, charging) = batterySnapshot(applicationContext)
                val payload = payloadBuilder.buildTrackingPayload(
                    profile = currentProfile,
                    location = currentLocation,
                    batteryLevel = batteryLevel,
                    charging = charging,
                    serialNumber = config.serialNumber,
                    id = config.id
                )
                mqttManager?.publish(config.personelDataTopic, payload, MqttManager.QOS_DATA)
            }
        }
    }

    private var lastPublishAt = 0L

    private fun startHeartRateImmediatePublisher() {
        heartRateJob?.cancel()
        heartRateJob = serviceScope.launch {
            com.example.dronepilottracking2026.core.bluetooth.BluetoothLeService.bpm.collect { bpm ->
                val previous = lastHeartRate
                lastHeartRate = bpm
                if (previous == bpm) return@collect
                val now = System.currentTimeMillis()
                if (now - lastHeartRatePublishAt < 250L) return@collect
                lastHeartRatePublishAt = now
                publishCurrentTracking()
            }
        }
    }

    private fun publishCurrentTracking() {
        serviceScope.launch {
            val currentLocation = lastLocation.value ?: return@launch
            val currentProfile = profile ?: return@launch
            val config = mqttConfig
            if (!config.isComplete) return@launch
            val (batteryLevel, charging) = batterySnapshot(applicationContext)
            val payload = payloadBuilder.buildTrackingPayload(
                profile = currentProfile,
                location = currentLocation,
                batteryLevel = batteryLevel,
                charging = charging,
                serialNumber = config.serialNumber,
                id = config.id
            )
            mqttManager?.publish(config.personelDataTopic, payload, MqttManager.QOS_DATA)
        }
    }

    private fun sendAvatar() {
        serviceScope.launch {
            val ready = withTimeoutOrNull(15_000L) {
                while (profile == null || lastLocation.value == null || !mqttConfig.isComplete) {
                    delay(100L)
                }
                true
            } == true
            val currentProfile = profile
            val currentLocation = lastLocation.value
            val config = mqttConfig
            val result = if (!ready || currentProfile == null || currentLocation == null || !config.isComplete) {
                Result.failure(IllegalStateException("Avatar pending; location or MQTT data is not ready"))
            } else {
                val (batteryLevel, charging) = batterySnapshot(applicationContext)
                val payload = payloadBuilder.buildTrackingPayload(
                    profile = currentProfile,
                    location = currentLocation,
                    batteryLevel = batteryLevel,
                    charging = charging,
                    serialNumber = config.serialNumber,
                    id = config.id,
                    includeAvatar = true
                )
                if (mqttManager?.publish(config.personelDataTopic, payload, MqttManager.QOS_DATA, kind = "AVATAR") == true) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Avatar pending; payload queued or publish failed"))
                }
            }
            _avatarSendResult.emit(result)
        }
    }

    private fun sendSos() {
        _sosActive.value = true
        serviceScope.launch {
            val ready = withTimeoutOrNull(15_000L) {
                while (profile == null || lastLocation.value == null || !mqttConfig.isComplete) {
                    delay(100L)
                }
                true
            } == true

            val currentProfile = profile
            val currentLocation = lastLocation.value
            val config = mqttConfig
            val result = if (!ready || currentProfile == null || currentLocation == null || !config.isComplete) {
                Result.failure(IllegalStateException("SOS active, waiting for location or MQTT data"))
            } else {
                val payload = payloadBuilder.buildSosPayload(currentProfile, currentLocation, serialNumber = config.serialNumber, id = config.id)
                val published = mqttManager?.publish(config.personelSosTopic, payload, MqttManager.QOS_SOS) == true
                if (published) Result.success(Unit)
                else Result.failure(IllegalStateException("SOS queued or failed to publish; check MQTT status"))
            }
            _sosResult.emit(result)
        }
    }

    private fun clearSos() {
        // Matikan indikator lokal segera saat tombol CLEAR SOS ditekan sekali.
        _sosActive.value = false

        serviceScope.launch {
            val currentProfile = profile
            val currentLocation = lastLocation.value
            val config = mqttConfig
            val result = when {
                currentProfile == null -> Result.failure(IllegalStateException("SOS cleared locally; personnel profile is not ready"))
                currentLocation == null -> Result.failure(IllegalStateException("SOS cleared locally; location is not available"))
                !config.isComplete -> Result.failure(IllegalStateException("SOS cleared locally; MQTT configuration is incomplete"))
                else -> {
                    val payload = payloadBuilder.buildSosPayload(currentProfile, currentLocation, sos = 0, serialNumber = config.serialNumber, id = config.id)
                    val published = mqttManager?.publish(config.personelSosTopic, payload, MqttManager.QOS_SOS) == true
                    if (published) Result.success(Unit)
                    else Result.failure(IllegalStateException("SOS clear queued or failed to publish; check MQTT status"))
                }
            }
            _sosResult.emit(result)
        }
    }

    private fun observeMqttConfiguration() {
        mqttConfigJob?.cancel()
        mqttConfigJob = serviceScope.launch {
            launch {
                ProfileDataStore(applicationContext).profile.collectLatest { profile = it }
            }
            MqttConfigDataStore(applicationContext).config.collectLatest { config ->
                val newInterval = config.intervalMs ?: DEFAULT_INTERVAL_MS
                val intervalChanged = newInterval != locationIntervalMs
                mqttConfig = config
                if (intervalChanged && locationJob?.isActive == true) {
                    locationIntervalMs = newInterval
                    lastPublishAt = 0L
                    startLocationUpdates(locationIntervalMs)
                } else {
                    locationIntervalMs = newInterval
                }
            }
        }
    }

    override fun onDestroy() {
        locationJob?.cancel()
        heartRateJob?.cancel()
        mqttConfigJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drone Pilot Tracking")
            .setContentText("Location tracking is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}

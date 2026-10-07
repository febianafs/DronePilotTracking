package com.example.dronepilottracking2026.core.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.dronepilottracking2026.DronePilotApplication
import com.example.dronepilottracking2026.R
import com.example.dronepilottracking2026.core.bluetooth.BluetoothLeService
import com.example.dronepilottracking2026.core.dmr.DmrPayloadFormatter
import com.example.dronepilottracking2026.core.dmr.DmrTransport
import com.example.dronepilottracking2026.core.mqtt.MqttManager
import com.example.dronepilottracking2026.core.mqtt.MqttPayloadBuilder
import com.example.dronepilottracking2026.core.mqtt.batterySnapshot
import com.example.dronepilottracking2026.data.local.DeliverySettingsDataStore
import com.example.dronepilottracking2026.data.local.DmrSequenceDataStore
import com.example.dronepilottracking2026.data.local.MqttConfigDataStore
import com.example.dronepilottracking2026.data.local.ProfileDataStore
import com.example.dronepilottracking2026.data.model.DEFAULT_DMR_INTERVAL_MS
import com.example.dronepilottracking2026.data.model.DEFAULT_DMR_SLOT
import com.example.dronepilottracking2026.data.model.DMR_CYCLE_MS
import com.example.dronepilottracking2026.data.model.DMR_SOS_REPEAT_INTERVAL_MS
import com.example.dronepilottracking2026.data.model.DeliveryMode
import com.example.dronepilottracking2026.data.model.LocationData
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import com.example.dronepilottracking2026.data.model.isDmrSlotWindow
import com.example.dronepilottracking2026.data.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private data class DmrSettingsSnapshot(
    val mode: DeliveryMode,
    val config: MqttConfig,
    val intervalMs: Long,
    val slot: Int
)

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
        private const val SOS_PREFS = "sos_state"
        private const val SOS_ACTIVE_KEY = "active"

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
    private lateinit var dmrTransport: DmrTransport
    private lateinit var sequenceDataStore: DmrSequenceDataStore
    private var settingsJob: Job? = null
    private var dmrSchedulerJob: Job? = null
    private var dmrSosRepeatJob: Job? = null
    private var internetSosRepeatJob: Job? = null
    private var sosSendJob: Job? = null
    private var mqttManager: MqttManager? = null
    private var mqttConfig: MqttConfig = MqttConfig()
    private var profile: PersonnelProfile? = null
    private lateinit var payloadBuilder: MqttPayloadBuilder
    private var deliveryMode = DeliveryMode.INTERNET
    private var dmrIntervalMs = DEFAULT_DMR_INTERVAL_MS
    private var dmrSlot = DEFAULT_DMR_SLOT
    private var locationIntervalMs = DEFAULT_INTERVAL_MS
    private var heartRateJob: kotlinx.coroutines.Job? = null
    private var lastHeartRate = com.example.dronepilottracking2026.core.bluetooth.BluetoothLeService.bpm.value
    private var lastHeartRatePublishAt = 0L
    private var lastMqttPublishAt = 0L

    private var lastDmrAttemptAt = 0L
    private var lastDmrCycle = Long.MIN_VALUE
    private var lastDmrSentLocation: LocationData? = null
    private var dmrForceNextData = false
    private var pendingDmrFrame: PendingDmrFrame? = null

    private data class PendingDmrFrame(
        val type: String,
        val sequence: Long,
        val payload: String,
        val location: LocationData
    )

    override fun onCreate() {
        super.onCreate()
        _sosActive.value = getSharedPreferences(SOS_PREFS, MODE_PRIVATE).getBoolean(SOS_ACTIVE_KEY, false)
        repository = LocationRepository(applicationContext)
        payloadBuilder = MqttPayloadBuilder(applicationContext)
        dmrTransport = DmrTransport(applicationContext)
        sequenceDataStore = DmrSequenceDataStore(applicationContext)
        createNotificationChannel()
    }

    private fun setSosActive(active: Boolean) {
        _sosActive.value = active
        getSharedPreferences(SOS_PREFS, MODE_PRIVATE).edit().putBoolean(SOS_ACTIVE_KEY, active).apply()
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
        observeSettings()
        startLocationUpdates(locationIntervalMs)
        startHeartRatePublisher()
        startDmrScheduler()
    }

    private fun startLocationUpdates(intervalMs: Long) {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            repository.locationFlow(intervalMs).collect { result ->
                _locationUpdates.emit(result)
                val currentLocation = result.getOrNull() ?: return@collect
                _lastLocation.value = currentLocation
                when (deliveryMode) {
                    DeliveryMode.INTERNET -> publishInternetTracking(currentLocation)
                    DeliveryMode.DMR -> Unit
                }
            }
        }
    }

    private suspend fun publishInternetTracking(location: LocationData) {
        val currentProfile = profile ?: return
        val config = mqttConfig
        if (!config.isComplete) return
        val interval = locationIntervalMs
        val now = System.currentTimeMillis()
        if (now - lastMqttPublishAt < interval) return
        lastMqttPublishAt = now
        val (batteryLevel, charging) = batterySnapshot(applicationContext)
        val payload = payloadBuilder.buildTrackingPayload(
            profile = currentProfile,
            location = location,
            batteryLevel = batteryLevel,
            charging = charging,
            serialNumber = config.serialNumber
        )
        mqttManager?.publish(config.personelDataTopic, payload, MqttManager.QOS_DATA, kind = "LOCATION")
    }

    private suspend fun handleDmrLocation(location: LocationData) {
        if (_sosActive.value || !dmrTransport.readiness().isReady || profile?.id.isNullOrBlank()) return

        val pending = pendingDmrFrame
        if (pending != null) {
            trySendPendingDmr(pending)
            return
        }

        if (!dmrForceNextData && !isDmrSlotDue()) return

        createAndSendDmrFrame(type = "D", location = location)
    }

    private fun isDmrSlotDue(now: Long = System.currentTimeMillis()): Boolean {
        val cycle = Math.floorDiv(now, DMR_CYCLE_MS)
        if (cycle == lastDmrCycle) return false
        return isDmrSlotWindow(now, dmrSlot)
    }

    private suspend fun awaitDmrSlotWindow() {
        while (!isDmrSlotWindow(System.currentTimeMillis(), dmrSlot)) {
            delay(100L)
        }
    }

    private suspend fun createAndSendDmrFrame(type: String, location: LocationData) {
        val currentProfile = profile ?: return
        val deviceId = currentProfile.id.trim()
        if (deviceId.isBlank()) return
        val sequence = sequenceDataStore.nextSequence()
        val (batteryLevel, _) = batterySnapshot(applicationContext)
        val payload = if (type == "S") {
            DmrPayloadFormatter.sos(
                sequence = sequence,
                id = deviceId,
                profile = currentProfile,
                location = location,
                heartRate = BluetoothLeService.heartRateForPayload(),
                batteryLevel = batteryLevel
            )
        } else {
            DmrPayloadFormatter.data(
                sequence = sequence,
                id = deviceId,
                profile = currentProfile,
                location = location,
                heartRate = BluetoothLeService.heartRateForPayload(),
                batteryLevel = batteryLevel
            )
        }
        val frame = PendingDmrFrame(type, sequence, payload, location)
        pendingDmrFrame = frame
        trySendPendingDmr(frame)
    }

    private suspend fun trySendPendingDmr(frame: PendingDmrFrame) {
        if (pendingDmrFrame?.sequence != frame.sequence) return
        val now = System.currentTimeMillis()
        if (!isDmrSlotDue(now)) return
        lastDmrAttemptAt = now
        lastDmrCycle = Math.floorDiv(now, DMR_CYCLE_MS)
        val result = dmrTransport.send(frame.payload)
        if (result.isSuccess) {
            pendingDmrFrame = null
            lastDmrSentLocation = frame.location
            dmrForceNextData = false
        }
    }

    private fun startDmrScheduler() {
        dmrSchedulerJob?.cancel()
        dmrSchedulerJob = serviceScope.launch {
            while (isActive) {
                if (deliveryMode != DeliveryMode.DMR ||
                    !dmrTransport.readiness().isReady ||
                    profile?.id.isNullOrBlank()
                ) {
                    delay(250L)
                    continue
                }

                val pending = pendingDmrFrame
                if (pending != null) {
                    trySendPendingDmr(pending)
                } else if (!_sosActive.value && isDmrSlotDue()) {
                    lastLocation.value?.let { location ->
                        createAndSendDmrFrame(type = "D", location = location)
                    }
                }
                delay(100L)
            }
        }
    }

    private fun startHeartRatePublisher() {
        heartRateJob?.cancel()
        heartRateJob = serviceScope.launch {
            BluetoothLeService.bpm.collect { bpm ->
                val previous = lastHeartRate
                lastHeartRate = bpm
                if (previous == bpm) return@collect
                val now = System.currentTimeMillis()
                if (now - lastHeartRatePublishAt < 250L) return@collect
                lastHeartRatePublishAt = now
                if (deliveryMode == DeliveryMode.INTERNET) {
                    publishCurrentInternetTracking()
                }
            }
        }
    }

    private fun publishCurrentInternetTracking() {
        serviceScope.launch {
            val currentLocation = lastLocation.value ?: return@launch
            publishInternetTracking(currentLocation)
        }
    }

    private fun sendAvatar() {
        serviceScope.launch {
            val result = try {
                withTimeoutOrNull(15_000L) {
                    // The in-memory profile collector may still hold the previous avatar
                    // immediately after the user saves an updated profile.
                    val currentProfile = ProfileDataStore(applicationContext).profile.first {
                        !it?.avatarUri.isNullOrBlank()
                    } ?: throw IllegalStateException("Avatar is not available in the saved profile")
                    profile = currentProfile

                    while (lastLocation.value == null || !mqttConfig.isComplete) delay(100L)

                    val currentLocation = lastLocation.value
                        ?: throw IllegalStateException("Location is not available")
                    val config = mqttConfig
                    val (batteryLevel, charging) = batterySnapshot(applicationContext)
                    val payload = payloadBuilder.buildTrackingPayload(
                        profile = currentProfile,
                        location = currentLocation,
                        batteryLevel = batteryLevel,
                        charging = charging,
                        serialNumber = config.serialNumber,
                        includeAvatar = true
                    )
                    if (mqttManager?.publish(
                            config.personelDataTopic,
                            payload,
                            MqttManager.QOS_DATA,
                            kind = "AVATAR"
                        ) == true
                    ) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Avatar queued; waiting for MQTT connection"))
                    }
                } ?: Result.failure(IllegalStateException("Timed out preparing avatar payload"))
            } catch (error: Exception) {
                Result.failure(error)
            }
            _avatarSendResult.emit(result)
        }
    }

    private fun sendSos() {
        _sosActive.value = true
        dmrSosRepeatJob?.cancel()
        internetSosRepeatJob?.cancel()
        sosSendJob?.cancel()
        setSosActive(true)
        sosSendJob = serviceScope.launch {
            val ready = withTimeoutOrNull(15_000L) {
                while (
                    profile == null ||
                    lastLocation.value == null ||
                    (deliveryMode == DeliveryMode.INTERNET && !mqttConfig.isComplete) ||
                    (deliveryMode == DeliveryMode.DMR &&
                        (!dmrTransport.readiness().isReady || profile?.id.isNullOrBlank()))
                ) {
                    delay(100L)
                }
                true
            } == true

            val currentProfile = profile
            val currentLocation = lastLocation.value
            val result: Result<Unit> = when {
                !ready || currentProfile == null || currentLocation == null -> {
                    Result.failure(IllegalStateException("SOS active, waiting for location or delivery readiness"))
                }
                deliveryMode == DeliveryMode.DMR -> {
                    createAndSendDmrFrame(type = "S", location = currentLocation)
                    startDmrSosRepeater()
                    Result.success(Unit)
                }
                else -> {
                    val config = mqttConfig
                    if (!config.isComplete) {
                        Result.failure(IllegalStateException("SOS active, waiting for location or MQTT data"))
                    } else {
                        awaitDmrSlotWindow()
                        val payload = payloadBuilder.buildSosPayload(
                            currentProfile,
                            currentLocation,
                            serialNumber = config.serialNumber,
                            id = currentProfile.id
                        )
                        val published = mqttManager?.publish(
                            config.personelSosTopic,
                            payload,
                            MqttManager.QOS_SOS,
                            kind = "SOS"
                        ) == true
                        startInternetSosRepeater()
                        if (published) Result.success(Unit)
                        else Result.failure(IllegalStateException("SOS queued or failed to publish; check MQTT status"))
                    }
                }
            }
            _sosResult.emit(result)
        }
    }

    private fun startDmrSosRepeater() {
        dmrSosRepeatJob?.cancel()
        dmrSosRepeatJob = serviceScope.launch {
            while (isActive && _sosActive.value && deliveryMode == DeliveryMode.DMR) {
                delay(DMR_SOS_REPEAT_INTERVAL_MS)
                if (!isActive || !_sosActive.value || deliveryMode != DeliveryMode.DMR) break
                val location = lastLocation.value ?: continue
                createAndSendDmrFrame(type = "S", location = location)
            }
        }
    }

    private fun startInternetSosRepeater() {
        internetSosRepeatJob?.cancel()
        internetSosRepeatJob = serviceScope.launch {
            while (isActive && _sosActive.value && deliveryMode == DeliveryMode.INTERNET) {
                delay(DMR_SOS_REPEAT_INTERVAL_MS)
                if (!isActive || !_sosActive.value || deliveryMode != DeliveryMode.INTERNET) break
                val currentProfile = profile ?: continue
                val currentLocation = lastLocation.value ?: continue
                val config = mqttConfig
                if (!config.isComplete) continue
                awaitDmrSlotWindow()
                if (!isActive || !_sosActive.value || deliveryMode != DeliveryMode.INTERNET) break
                val payload = payloadBuilder.buildSosPayload(
                    currentProfile,
                    currentLocation,
                    serialNumber = config.serialNumber,
                    id = currentProfile.id
                )
                mqttManager?.publish(
                    config.personelSosTopic,
                    payload,
                    MqttManager.QOS_SOS,
                    kind = "SOS"
                )
            }
        }
    }

    private fun clearSos() {
        // Matikan indikator lokal segera saat tombol CLEAR SOS ditekan sekali.
        setSosActive(false)
        _sosActive.value = false
        dmrSosRepeatJob?.cancel()
        dmrSosRepeatJob = null
        internetSosRepeatJob?.cancel()
        internetSosRepeatJob = null
        sosSendJob?.cancel()
        dmrForceNextData = true

        sosSendJob = serviceScope.launch {
            val currentProfile = profile
            val currentLocation = lastLocation.value
            if (deliveryMode == DeliveryMode.DMR) {
                if (currentProfile?.id.isNullOrBlank()) {
                    _sosResult.emit(Result.failure(IllegalStateException("Personnel profile ID is required for DMR")))
                    return@launch
                }
                if (currentProfile != null && currentLocation != null) {
                    createAndSendDmrFrame(type = "D", location = currentLocation)
                }
                _sosResult.emit(Result.success(Unit))
                return@launch
            }

            val config = mqttConfig
            val result = when {
                currentProfile == null -> Result.failure(IllegalStateException("SOS cleared locally; personnel profile is not ready"))
                currentLocation == null -> Result.failure(IllegalStateException("SOS cleared locally; location is not available"))
                !config.isComplete -> Result.failure(IllegalStateException("SOS cleared locally; MQTT configuration is incomplete"))
                else -> {
                    awaitDmrSlotWindow()
                    val payload = payloadBuilder.buildSosPayload(currentProfile, currentLocation, sos = 0, serialNumber = config.serialNumber, id = currentProfile.id)
                    val published = mqttManager?.publish(config.personelSosTopic, payload, MqttManager.QOS_SOS, kind = "SOS_CLEAR") == true
                    if (published) Result.success(Unit)
                    else Result.failure(IllegalStateException("SOS clear queued or failed to publish; check MQTT status"))
                }
            }
            _sosResult.emit(result)
        }
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            launch {
                ProfileDataStore(applicationContext).profile.collectLatest { profile = it }
            }

            combine(
                DeliverySettingsDataStore(applicationContext).mode,
                MqttConfigDataStore(applicationContext).config,
                DeliverySettingsDataStore(applicationContext).dmrIntervalMs,
                DeliverySettingsDataStore(applicationContext).dmrSlot
            ) { mode, config, dmrInterval, slot ->
                DmrSettingsSnapshot(mode, config, dmrInterval, slot)
            }.collectLatest { (mode, config, dmrInterval, slot) ->
                val mqttInterval = config.intervalMs ?: DEFAULT_INTERVAL_MS
                val effectiveInterval = if (mode == DeliveryMode.DMR) {
                    dmrInterval.coerceAtLeast(DEFAULT_DMR_INTERVAL_MS)
                } else {
                    mqttInterval
                }
                val intervalChanged = effectiveInterval != locationIntervalMs
                deliveryMode = mode
                mqttConfig = config
                dmrIntervalMs = dmrInterval.coerceAtLeast(DEFAULT_DMR_INTERVAL_MS)
                dmrSlot = slot
                locationIntervalMs = effectiveInterval

                if (intervalChanged && locationJob?.isActive == true) {
                    lastMqttPublishAt = 0L
                    startLocationUpdates(locationIntervalMs)
                } else if (locationJob?.isActive != true) {
                    startLocationUpdates(locationIntervalMs)
                }

                if (mode == DeliveryMode.DMR) {
                    mqttManager?.disableAutoReconnect()
                } else if (config.isComplete) {
                    mqttManager?.connect(config)
                }
            }
        }
    }

    private fun distanceMeters(a: LocationData, b: LocationData): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            results
        )
        return results[0]
    }

    override fun onDestroy() {
        locationJob?.cancel()
        heartRateJob?.cancel()
        mqttConfigJob?.cancel()
        dmrSchedulerJob?.cancel()
        dmrSosRepeatJob?.cancel()
        internetSosRepeatJob?.cancel()
        sosSendJob?.cancel()
        settingsJob?.cancel()
        mqttManager?.disconnect()
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
            .setSmallIcon(R.drawable.logopst)
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

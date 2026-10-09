package com.example.dronepilottracking2026.ui.mqtt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.dronepilottracking2026.DronePilotApplication
import com.example.dronepilottracking2026.core.dmr.DmrNotificationListener
import com.example.dronepilottracking2026.core.dmr.DmrTransport
import com.example.dronepilottracking2026.core.mqtt.MqttManager
import com.example.dronepilottracking2026.core.mqtt.MqttManagerState
import com.example.dronepilottracking2026.data.local.DeliverySettingsDataStore
import com.example.dronepilottracking2026.data.local.MqttConfigDataStore
import com.example.dronepilottracking2026.data.model.DeliveryMode
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.example.dronepilottracking2026.data.model.MqttConnectionState
import com.example.dronepilottracking2026.data.model.MqttUiState
import com.example.dronepilottracking2026.data.repository.MqttConfigRepository
import com.example.dronepilottracking2026.core.location.LocationTrackingService
import com.example.dronepilottracking2026.core.network.networkStatusFlow
import com.example.dronepilottracking2026.data.model.CONNECTIVITY_BANNER_HIDE_DELAY_MS
import com.example.dronepilottracking2026.data.model.CONNECTIVITY_BANNER_SHOW_DELAY_MS
import com.example.dronepilottracking2026.data.model.CONNECTIVITY_BANNER_SOS_SHOW_DELAY_MS
import com.example.dronepilottracking2026.data.model.ConnectivityIssue
import com.example.dronepilottracking2026.data.model.connectivityIssue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class DeliverySettingsSnapshot(
    val config: MqttConfig,
    val mode: DeliveryMode,
    val dmrIntervalMs: Long,
    val dmrSlot: Int
)

class MqttViewModel(application: Application) : AndroidViewModel(application) {
    private val configRepository = MqttConfigRepository(MqttConfigDataStore(application.applicationContext))
    private val deliverySettings = DeliverySettingsDataStore(application.applicationContext)
    companion object { private const val TEST_RESULT_DISPLAY_MS = 4_000L }
    private val manager = (application as DronePilotApplication).mqttManager
    private val dmrTransport = DmrTransport(application.applicationContext)
    private val _uiState = MutableStateFlow(MqttUiState())
    val uiState: StateFlow<MqttUiState> = _uiState.asStateFlow()
    private var testRequestId = 0L
    private var testResultClearJob: Job? = null
    private val lastFailureRejected = MutableStateFlow(false)

    /** Delivery problem to show in the banner, delayed so brief signal drops do not flicker it. */
    val connectivityIssue: StateFlow<ConnectivityIssue?> = combine(
        networkStatusFlow(application),
        manager.connectionState,
        lastFailureRejected,
        _uiState,
        LocationTrackingService.sosActive
    ) { network, mqttState, rejected, ui, sosActive ->
        connectivityIssue(
            deliveryMode = ui.deliveryMode,
            mqttConfigured = ui.config.isComplete,
            network = network,
            mqttConnected = mqttState == MqttManagerState.CONNECTED,
            lastFailureRejected = rejected
        ) to sosActive
    }
        .distinctUntilChanged()
        .delayedBanner()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            manager.connectionState.collect { state ->
                when (state) {
                    is MqttManagerState.ERROR -> lastFailureRejected.value = state.rejected
                    MqttManagerState.CONNECTED -> lastFailureRejected.value = false
                    else -> Unit
                }
                _uiState.update { it.copy(connectionState = state.toUiState(), error = state.errorMessage()) }
            }
        }
        viewModelScope.launch {
            manager.publishEvents.collect { event ->
                if (event.success) {
                    _uiState.update {
                        it.copy(
                            error = null,
                            publishStatus = "MQTT broker accepted publish for ${event.topic}"
                        )
                    }
                } else {
                    val message = if (event.reason == "Queued offline") {
                        "Message queued for retry on topic: ${event.topic}"
                    } else {
                        "Publish failed for topic ${event.topic}: ${event.reason.orEmpty()}"
                    }
                    _uiState.update { it.copy(error = message, publishStatus = message) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                configRepository.config,
                deliverySettings.mode,
                deliverySettings.dmrIntervalMs,
                deliverySettings.dmrSlot
            ) { config, mode, dmrInterval, dmrSlot ->
                DeliverySettingsSnapshot(config, mode, dmrInterval, dmrSlot)
            }.collectLatest { (config, mode, dmrInterval, dmrSlot) ->
                    _uiState.update {
                        it.copy(
                            config = config,
                            deliveryMode = mode,
                            dmrIntervalMs = dmrInterval,
                            dmrSlot = dmrSlot,
                            dmrNotificationAccessGranted = dmrTransport.isNotificationAccessGranted(),
                            dmrReadiness = dmrTransport.readiness(),
                            dmrSendStatus = DmrTransport.lastSendStatus.value,
                            connectionState = when {
                                mode == DeliveryMode.DMR -> MqttConnectionState.DISCONNECTED
                                !config.isComplete -> MqttConnectionState.NOT_CONFIGURED
                                else -> it.connectionState
                            }
                        )
                    }
                    if (mode == DeliveryMode.DMR) {
                        manager.disableAutoReconnect()
                    } else if (config.isComplete) {
                        manager.connect(config)
                    }
                }
        }

        viewModelScope.launch {
            DmrTransport.lastSendStatus.collectLatest { status ->
                _uiState.update { it.copy(dmrSendStatus = status) }
            }
        }

        viewModelScope.launch {
            combine(
                DmrNotificationListener.listenerConnected,
                DmrNotificationListener.tookerNotificationDetected
            ) { _, _ -> dmrTransport.readiness() }
                .collectLatest { readiness ->
                    _uiState.update { it.copy(dmrReadiness = readiness) }
                }
        }
    }

    fun setDeliveryMode(mode: DeliveryMode) {
        viewModelScope.launch {
            deliverySettings.setMode(mode)
            if (mode == DeliveryMode.DMR) manager.disableAutoReconnect()
        }
    }

    fun refreshDmrReadiness() {
        _uiState.update {
            it.copy(
                dmrReadiness = dmrTransport.readiness(),
                dmrNotificationAccessGranted = dmrTransport.isNotificationAccessGranted(),
                dmrSendStatus = DmrTransport.lastSendStatus.value
            )
        }
    }

    fun saveDmrInterval(intervalMs: Long) {
        viewModelScope.launch { deliverySettings.setDmrInterval(intervalMs) }
    }

    fun saveDmrSlot(slot: Int) {
        viewModelScope.launch { deliverySettings.setDmrSlot(slot) }
    }

    fun openDmrNotificationAccessSettings() {
        dmrTransport.openNotificationAccessSettings()
    }

    fun openDmrApp() {
        val opened = dmrTransport.openApp()
        val readiness = dmrTransport.readiness()
        _uiState.update {
            it.copy(
                dmrReadiness = readiness,
                error = if (opened) null else readiness.failureMessage()
            )
        }
    }

    fun saveSerialNumber(serialNumber: String) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(serialNumber = serialNumber)
            configRepository.save(updated)
            _uiState.update { it.copy(config = updated) }
        }
    }

    fun saveSerialNumberAndId(serialNumber: String, id: String) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(
                serialNumber = serialNumber,
                id = id
            )
            configRepository.save(updated)
            _uiState.update { it.copy(config = updated) }
        }
    }

    fun saveInterval(intervalMs: Long) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(intervalMs = intervalMs)
            configRepository.save(updated)
            _uiState.update { it.copy(config = updated) }
        }
    }

    fun saveAndConnect(config: MqttConfig) {
        if (!config.isComplete) {
            _uiState.update { it.copy(error = "MQTT configuration is incomplete") }
            return
        }
        viewModelScope.launch {
            configRepository.save(config)
            _uiState.update { it.copy(config = config, saved = true, error = null) }
            if (_uiState.value.deliveryMode == DeliveryMode.INTERNET) {
                manager.connect(config)
            }
        }
    }

    fun testConnection(config: MqttConfig) {
        if (_uiState.value.deliveryMode != DeliveryMode.INTERNET) {
            _uiState.update { it.copy(error = "MQTT is disabled while DMR mode is active") }
            return
        }
        val requestId = ++testRequestId
        testResultClearJob?.cancel()
        if (!config.isConnectionComplete) {
            _uiState.update { it.copy(testResult = "MQTT connection fields are incomplete") }
            scheduleTestResultClear(requestId, "MQTT connection fields are incomplete")
            return
        }
        _uiState.update {
            it.copy(testResult = "Testing MQTT connection...")
        }
        manager.testConnection(config) { result ->
            result.fold(
                onSuccess = {
                    showTestResult(requestId, "Connection test succeeded")
                },
                onFailure = { _ ->
                    showTestResult(requestId, "Connection test failed")
                }
            )
        }
    }

    private fun showTestResult(requestId: Long, result: String) {
        viewModelScope.launch {
            if (requestId != testRequestId) return@launch
            _uiState.update { it.copy(testResult = result) }
            scheduleTestResultClear(requestId, result)
        }
    }

    private fun scheduleTestResultClear(requestId: Long, result: String) {
        testResultClearJob?.cancel()
        testResultClearJob = viewModelScope.launch {
            delay(TEST_RESULT_DISPLAY_MS)
            if (requestId == testRequestId && _uiState.value.testResult == result) {
                _uiState.update { it.copy(testResult = null) }
            }
        }
    }

    /**
     * Shows an issue only after it persists (shorter while SOS is active) and hides it only after
     * delivery has been healthy for a while. A visible banner switches to a new issue immediately.
     */
    private fun Flow<Pair<ConnectivityIssue?, Boolean>>.delayedBanner(): Flow<ConnectivityIssue?> = channelFlow {
        var visible: ConnectivityIssue? = null
        collectLatest { (issue, sosActive) ->
            when {
                issue == null -> if (visible != null) {
                    delay(CONNECTIVITY_BANNER_HIDE_DELAY_MS)
                    visible = null
                    send(null)
                }
                visible != null -> {
                    visible = issue
                    send(issue)
                }
                else -> {
                    delay(if (sosActive) CONNECTIVITY_BANNER_SOS_SHOW_DELAY_MS else CONNECTIVITY_BANNER_SHOW_DELAY_MS)
                    visible = issue
                    send(issue)
                }
            }
        }
    }

    private fun MqttManagerState.toUiState(): MqttConnectionState = when (this) {
        MqttManagerState.CONNECTING -> MqttConnectionState.CONNECTING
        MqttManagerState.CONNECTED -> MqttConnectionState.CONNECTED
        MqttManagerState.DISCONNECTED -> MqttConnectionState.DISCONNECTED
        is MqttManagerState.ERROR -> MqttConnectionState.DISCONNECTED
    }

    private fun MqttManagerState.errorMessage(): String? =
        (this as? MqttManagerState.ERROR)?.message
}

package com.example.dronepilottracking2026.ui.mqtt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.dronepilottracking2026.DronePilotApplication
import com.example.dronepilottracking2026.core.mqtt.MqttManager
import com.example.dronepilottracking2026.core.mqtt.MqttManagerState
import com.example.dronepilottracking2026.data.local.MqttConfigDataStore
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.example.dronepilottracking2026.data.model.MqttConnectionState
import com.example.dronepilottracking2026.data.model.MqttUiState
import com.example.dronepilottracking2026.data.repository.MqttConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MqttViewModel(application: Application) : AndroidViewModel(application) {
    companion object { private const val TEST_RESULT_DISPLAY_MS = 4_000L }
    private val repository = MqttConfigRepository(MqttConfigDataStore(application.applicationContext))
    private val manager = (application as DronePilotApplication).mqttManager
    private val _uiState = MutableStateFlow(MqttUiState())
    val uiState: StateFlow<MqttUiState> = _uiState.asStateFlow()
    private var testRequestId = 0L
    private var testResultClearJob: Job? = null

    init {
        viewModelScope.launch {
            manager.connectionState.collect { state ->
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
            repository.config.collectLatest { config ->
                _uiState.update {
                    it.copy(
                        config = config,
                        connectionState = if (config.isComplete) it.connectionState else MqttConnectionState.NOT_CONFIGURED
                    )
                }
                if (config.isComplete) manager.connect(config)
            }
        }
    }

    fun saveSerialNumber(serialNumber: String) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(serialNumber = serialNumber)
            repository.save(updated)
            _uiState.update { it.copy(config = updated) }
        }
    }

    fun saveInterval(intervalMs: Long) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(intervalMs = intervalMs)
            repository.save(updated)
            _uiState.update { it.copy(config = updated) }
        }
    }

    fun saveAndConnect(config: MqttConfig) {
        if (!config.isComplete) {
            _uiState.update { it.copy(error = "MQTT configuration is incomplete") }
            return
        }
        viewModelScope.launch {
            repository.save(config)
            _uiState.update { it.copy(config = config, saved = true, error = null) }
        }
    }

    fun testConnection(config: MqttConfig) {
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

    private fun MqttManagerState.toUiState(): MqttConnectionState = when (this) {
        MqttManagerState.CONNECTING -> MqttConnectionState.CONNECTING
        MqttManagerState.CONNECTED -> MqttConnectionState.CONNECTED
        MqttManagerState.DISCONNECTED -> MqttConnectionState.DISCONNECTED
        is MqttManagerState.ERROR -> MqttConnectionState.DISCONNECTED
    }

    private fun MqttManagerState.errorMessage(): String? =
        (this as? MqttManagerState.ERROR)?.message
}

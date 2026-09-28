package com.example.dronepilottracking2026.ui.mqtt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val repository = MqttConfigRepository(MqttConfigDataStore(application.applicationContext))
    private val manager = (application as DronePilotApplication).mqttManager
    private val _uiState = MutableStateFlow(MqttUiState())
    val uiState: StateFlow<MqttUiState> = _uiState.asStateFlow()

    init {
        manager.onStateChanged = { state ->
            _uiState.update { it.copy(connectionState = state.toUiState(), error = state.errorMessage()) }
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

    fun saveAndConnect(config: MqttConfig) {
        if (!config.isComplete) {
            _uiState.update { it.copy(error = "MQTT configuration is incomplete") }
            return
        }
        viewModelScope.launch {
            repository.save(config)
            _uiState.update { it.copy(config = config, saved = true, error = null) }
            manager.connect(config)
        }
    }

    fun testConnection(config: MqttConfig) {
        if (!config.isConnectionComplete) {
            _uiState.update { it.copy(error = "MQTT connection fields are incomplete") }
            return
        }
        _uiState.update {
            it.copy(connectionState = MqttConnectionState.CONNECTING, error = null, saved = false)
        }
        manager.testConnection(config) { result ->
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(connectionState = MqttConnectionState.CONNECTED, error = null)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            connectionState = MqttConnectionState.ERROR,
                            error = error.message ?: "MQTT test connection failed"
                        )
                    }
                }
            )
        }
    }

    private fun MqttManagerState.toUiState(): MqttConnectionState = when (this) {
        MqttManagerState.CONNECTING -> MqttConnectionState.CONNECTING
        MqttManagerState.CONNECTED -> MqttConnectionState.CONNECTED
        MqttManagerState.DISCONNECTED -> MqttConnectionState.DISCONNECTED
        is MqttManagerState.ERROR -> MqttConnectionState.ERROR
    }

    private fun MqttManagerState.errorMessage(): String? =
        (this as? MqttManagerState.ERROR)?.message
}

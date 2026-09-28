package com.example.dronepilottracking2026.data.model

data class MqttConfig(
    val host: String = "",
    val tcpPort: Int? = null,
    val wsPort: Int? = null,
    val username: String = "",
    val password: String = "",
    val useWebSocket: Boolean = false,
    val personelDataTopic: String = "",
    val personelSosTopic: String = "",
    val intervalMs: Long? = 5_000L
) {
    val isConnectionComplete: Boolean
        get() = host.isNotBlank() &&
            tcpPort != null &&
            wsPort != null &&
            username.isNotBlank() &&
            password.isNotBlank()

    val isComplete: Boolean
        get() = isConnectionComplete &&
            personelDataTopic.isNotBlank() &&
            personelSosTopic.isNotBlank() &&
            intervalMs != null && intervalMs > 0L
}

data class TrackingPayload(
    val timestamp: Long,
    val serialNumber: String,
    val androidId: String,
    val appVersion: String,
    val identity: IdentityPayload,
    val gps: GpsPayload,
    val radioHealth: RadioHealthPayload,
    val battery: BatteryPayload
)

data class IdentityPayload(
    val id: String,
    val nrp: String,
    val name: String,
    val avatarUri: String
)

data class GpsPayload(
    val gpsTimestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val source: String,
    val satellites: Int
)

data class RadioHealthPayload(
    val heartrateTimestamp: Long,
    val heartrate: Int,
    val connected: Boolean
)

data class BatteryPayload(
    val batteryTimestamp: Long,
    val level: Int,
    val charging: Boolean
)

data class SosPayload(
    val timestamp: Long,
    val serialNumber: String,
    val androidId: String,
    val id: String,
    val name: String,
    val avatarUri: String,
    val sos: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float
)

enum class MqttConnectionState {
    NOT_CONFIGURED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class MqttUiState(
    val config: MqttConfig = MqttConfig(),
    val connectionState: MqttConnectionState = MqttConnectionState.NOT_CONFIGURED,
    val error: String? = null,
    val saved: Boolean = false
)

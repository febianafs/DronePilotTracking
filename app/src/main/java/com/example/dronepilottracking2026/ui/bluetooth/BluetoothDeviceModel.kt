package com.example.dronepilottracking2026.ui.bluetooth

data class BluetoothDeviceModel(
    val name: String,
    val address: String,
    val rssi: Int,
    val state: DeviceState = DeviceState.IDLE
)

enum class DeviceState {
    IDLE,
    CONNECTING,
    CONNECTED
}

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class HeartRateUiState(
    val bluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<BluetoothDeviceModel> = emptyList(),
    val lockedDevice: BluetoothDeviceModel? = null,
    val connectedDevice: BluetoothDeviceModel? = null,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val bpm: Int = 0,
    val error: String? = null
)

fun HeartRateUiState.heartStatus(): String = when {
    bpm == 0 -> "--"
    bpm < 60 -> "Below normal"
    bpm > 100 -> "Above normal"
    else -> "Normal"
}

package com.example.dronepilottracking2026.ui.bluetooth

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dronepilottracking2026.core.bluetooth.BluetoothLeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState.asStateFlow()
    private var scanCallback: ScanCallback? = null
    init {
        refreshBluetoothState()
        observeServiceState()
    }

    fun refreshBluetoothState() {
        _uiState.update { it.copy(bluetoothEnabled = adapter?.isEnabled == true) }
    }

    fun ensureService() {
        val context = getApplication<Application>()
        val intent = Intent(context, BluetoothLeService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Unable to start Bluetooth service") }
        }
    }

    fun startScan() {
        if (!hasBluetoothPermission()) return setError("Bluetooth permission is required")
        val bluetoothScanner = scanner
        if (bluetoothScanner == null || adapter?.isEnabled != true) {
            return setError("Bluetooth is disabled or scanner is unavailable")
        }
        stopScan()
        _uiState.update { it.copy(isScanning = true, devices = emptyList(), error = null) }
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = try { device.name.orEmpty().ifBlank { "Unnamed BLE device" } } catch (_: SecurityException) { return }
                val model = BluetoothDeviceModel(name, device.address, result.rssi)
                _uiState.update { state ->
                    state.copy(devices = state.devices.filterNot { it.address == model.address }.plus(model).sortedByDescending { it.rssi })
                }
            }
            override fun onScanFailed(errorCode: Int) = setError("BLE scan failed: $errorCode")
        }
        try { bluetoothScanner.startScan(scanCallback) } catch (_: SecurityException) { setError("Bluetooth permission is required") }
    }

    fun stopScan() {
        val callback = scanCallback ?: return
        try { if (hasBluetoothPermission()) scanner?.stopScan(callback) } catch (_: SecurityException) { }
        scanCallback = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connect(device: BluetoothDeviceModel) {
        if (!hasBluetoothPermission()) return setError("Bluetooth permission is required")
        val currentAdapter = adapter ?: return setError("Bluetooth is unavailable")
        val currentService = BluetoothLeService.getRunningService()
            ?: return setError("Bluetooth service is not ready")
        stopScan()
        currentService.connect(device, currentAdapter)
    }

    fun disconnect() = BluetoothLeService.getRunningService()?.disconnect()
    fun forgetDevice() = BluetoothLeService.getRunningService()?.forgetLockedDevice()
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun observeServiceState() {
        viewModelScope.launch {
            combine(BluetoothLeService.connectionState, BluetoothLeService.bpm, BluetoothLeService.connectedDevice, BluetoothLeService.lockedDevice) { connection, bpm, connected, locked ->
                Triple(connection, bpm, connected to locked)
            }.collect { (connection, bpm, devices) ->
                val (connected, locked) = devices
                _uiState.update { state ->
                    state.copy(
                        connectionState = connection,
                        bpm = bpm,
                        connectedDevice = connected,
                        lockedDevice = locked,
                        devices = state.devices.map { device ->
                            when {
                                device.address == connected?.address && connection == BleConnectionState.CONNECTED -> device.copy(state = DeviceState.CONNECTED)
                                device.address == connected?.address && connection == BleConnectionState.CONNECTING -> device.copy(state = DeviceState.CONNECTING)
                                else -> device.copy(state = DeviceState.IDLE)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun setError(message: String) { _uiState.update { it.copy(error = message, isScanning = false) } }

    override fun onCleared() {
        stopScan()
    }
}

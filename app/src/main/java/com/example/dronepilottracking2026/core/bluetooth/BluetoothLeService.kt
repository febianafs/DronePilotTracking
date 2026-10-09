package com.example.dronepilottracking2026.core.bluetooth

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.dronepilottracking2026.R
import com.example.dronepilottracking2026.ui.bluetooth.BleConnectionState
import com.example.dronepilottracking2026.ui.bluetooth.BluetoothDeviceModel
import com.example.dronepilottracking2026.ui.bluetooth.DeviceState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class BluetoothLeService : Service() {
    companion object {
        private const val TAG = "DroneBleService"
        private const val CHANNEL_ID = "heart_rate_ble"
        private const val NOTIFICATION_ID = 2101
        private const val PREFS_NAME = "heart_rate_locked_device"
        private const val KEY_NAME = "device_name"
        private const val KEY_ADDRESS = "device_address"
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val GATT_SETUP_TIMEOUT_MS = 15_000L
        private const val RECONNECT_SCAN_TIMEOUT_MS = 10_000L
        private const val HEART_RATE_STALE_TIMEOUT_MS = 10_000L

        val connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
        val bpm = MutableStateFlow(0)
        val bpmReading = MutableSharedFlow<Int>(extraBufferCapacity = 64)
        val connectedDevice = MutableStateFlow<BluetoothDeviceModel?>(null)
        val lockedDevice = MutableStateFlow<BluetoothDeviceModel?>(null)
        @Volatile private var currentInstance: BluetoothLeService? = null

        fun getRunningService(): BluetoothLeService? = currentInstance

        /** True when a heart rate device was paired before, so the service should reconnect on app start. */
        fun hasLockedDevice(context: Context): Boolean =
            !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ADDRESS, null).isNullOrBlank()

        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_CHARACTERISTIC: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun publishBpm(value: Int) {
            val normalized = value.coerceAtLeast(0)
            bpm.value = normalized
            bpmReading.tryEmit(normalized)
        }

        fun heartRateForPayload(): Int? =
            bpm.value.takeIf { connectionState.value == BleConnectionState.CONNECTED }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val heartRateStaleRunnable = Runnable {
        if (connectionState.value == BleConnectionState.CONNECTED) {
            Log.w(TAG, "Heart rate notifications stale; clearing BPM")
            publishBpm(0)
        }
    }
    private val gattSetupTimeoutRunnable = Runnable {
        if (connectionState.value == BleConnectionState.CONNECTING) {
            Log.w(TAG, "GATT connection or notification setup timed out")
            val timedOutGatt = bluetoothGatt
            bluetoothGatt = null
            handler.removeCallbacks(heartRateStaleRunnable)
            try {
                if (hasConnectPermission()) {
                    timedOutGatt?.disconnect()
                    timedOutGatt?.close()
                }
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to close timed out GATT connection", error)
            }
            connectionState.value = BleConnectionState.DISCONNECTED
            connectedDevice.value = lockedDevice.value
            publishBpm(0)
            if (!userRequestedDisconnect && lockedDevice.value != null) {
                updateNotification("Connection timed out; retrying")
                scheduleReconnect()
            } else {
                updateNotification("Bluetooth standby")
            }
        }
    }
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentAddress: String? = null
    private var userRequestedDisconnect = false
    private var reconnectScheduled = false
    private var reconnectScanCallback: ScanCallback? = null

    private val reconnectScanTimeoutRunnable = Runnable {
        if (reconnectScanCallback == null) return@Runnable
        Log.w(TAG, "Locked device not found while scanning; retrying")
        stopReconnectScan()
        if (connectionState.value == BleConnectionState.CONNECTING && bluetoothGatt == null) {
            connectionState.value = BleConnectionState.DISCONNECTED
            connectedDevice.value = lockedDevice.value
        }
        updateNotification("Waiting for ${lockedDevice.value?.name ?: "device"}")
        scheduleReconnect()
    }

    private val reconnectRunnable = Runnable {
        reconnectScheduled = false
        if (hasConnectPermission()) reconnectLockedDevice()
        else updateNotification("Bluetooth permission required")
    }

    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        lockedDevice.value = loadLockedDevice()
        currentAddress = lockedDevice.value?.address
        createNotificationChannel()
        if (!startAsForeground("Bluetooth standby")) {
            // Permission was revoked (e.g. sticky restart); stop instead of crashing.
            stopSelf()
            return
        }
        if (lockedDevice.value != null) scheduleReconnect()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (lockedDevice.value != null && connectionState.value == BleConnectionState.DISCONNECTED) {
            scheduleReconnect()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopReconnectScan()
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(gattSetupTimeoutRunnable)
        handler.removeCallbacks(heartRateStaleRunnable)
        disconnectGatt()
        if (currentInstance === this) currentInstance = null
        super.onDestroy()
    }

    fun connect(device: BluetoothDeviceModel, adapter: BluetoothAdapter) {
        if (!hasConnectPermission()) {
            updateNotification("Bluetooth permission required")
            return
        }
        userRequestedDisconnect = false
        stopReconnectScan()
        handler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        currentAddress = device.address
        saveLockedDevice(device)
        disconnectGatt()

        connectionState.value = BleConnectionState.CONNECTING
        connectedDevice.value = device.copy(state = DeviceState.CONNECTING)
        try {
            val rawDevice = adapter.getRemoteDevice(device.address)
            bluetoothGatt = rawDevice.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            handler.removeCallbacks(gattSetupTimeoutRunnable)
            handler.postDelayed(gattSetupTimeoutRunnable, GATT_SETUP_TIMEOUT_MS)
            updateNotification("Connecting to ${device.name}")
        } catch (e: SecurityException) {
            handler.removeCallbacks(gattSetupTimeoutRunnable)
            connectionState.value = BleConnectionState.DISCONNECTED
            connectedDevice.value = null
            updateNotification("Bluetooth permission required")
        }
    }

    fun disconnect() {
        userRequestedDisconnect = true
        stopReconnectScan()
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(gattSetupTimeoutRunnable)
        reconnectScheduled = false
        clearLockedDevice()
        disconnectGatt()
        currentAddress = null
        connectionState.value = BleConnectionState.DISCONNECTED
        connectedDevice.value = null
        publishBpm(0)
        updateNotification("Bluetooth standby")
    }

    fun forgetLockedDevice() = disconnect()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== bluetoothGatt) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        connectionState.value = BleConnectionState.CONNECTING
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "GATT discovery permission denied", e)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.removeCallbacks(gattSetupTimeoutRunnable)
                    handler.removeCallbacks(heartRateStaleRunnable)
                    try {
                        if (hasConnectPermission()) gatt.close()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "GATT close permission denied")
                    }
                    bluetoothGatt = null
                    connectionState.value = BleConnectionState.DISCONNECTED
                    connectedDevice.value = lockedDevice.value
                    publishBpm(0)
                    if (!userRequestedDisconnect && lockedDevice.value != null) {
                        updateNotification("Reconnecting to ${lockedDevice.value?.name}")
                        scheduleReconnect()
                    } else {
                        connectedDevice.value = null
                        updateNotification("Bluetooth standby")
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== bluetoothGatt || gatt.device.address != currentAddress) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT service discovery failed: $status")
                failGattSetup(gatt)
                return
            }

            val service = gatt.getService(HEART_RATE_SERVICE) ?: return failGattSetup(gatt)
            val characteristic = service.getCharacteristic(HEART_RATE_CHARACTERISTIC) ?: return failGattSetup(gatt)
            if (characteristic.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) {
                return failGattSetup(gatt)
            }
            if (!hasConnectPermission()) return failGattSetup(gatt)
            try {
                if (!gatt.setCharacteristicNotification(characteristic, true)) return failGattSetup(gatt)
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                    ?: return failGattSetup(gatt)
                descriptor.value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 &&
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!gatt.writeDescriptor(descriptor)) failGattSetup(gatt)
            } catch (e: SecurityException) {
                Log.e(TAG, "Unable to subscribe to heart rate notifications", e)
                failGattSetup(gatt)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== bluetoothGatt || gatt.device.address != currentAddress || descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIG) return
            handler.removeCallbacks(gattSetupTimeoutRunnable)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Heart rate notification subscription failed: $status")
                failGattSetup(gatt)
                return
            }
            connectionState.value = BleConnectionState.CONNECTED
            connectedDevice.value = lockedDevice.value?.copy(state = DeviceState.CONNECTED)
            updateNotification("Connected to ${connectedDevice.value?.name ?: "device"}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (gatt.device.address != currentAddress || characteristic.uuid != HEART_RATE_CHARACTERISTIC) return
            val result = BleHeartRateParser.parse(characteristic.value ?: return) ?: return
            val effectiveBpm = if (
                result.sensorContactSupported && !result.sensorContactDetected
            ) {
                0
            } else {
                result.bpm
            }
            handler.removeCallbacks(heartRateStaleRunnable)
            if (effectiveBpm > 0) handler.postDelayed(heartRateStaleRunnable, HEART_RATE_STALE_TIMEOUT_MS)
            publishBpm(effectiveBpm)
        }
    }

    private fun failGattSetup(gatt: BluetoothGatt) {
        if (gatt !== bluetoothGatt || gatt.device.address != currentAddress) return
        handler.removeCallbacks(gattSetupTimeoutRunnable)
        bluetoothGatt = null
        connectionState.value = BleConnectionState.DISCONNECTED
        connectedDevice.value = lockedDevice.value
        handler.removeCallbacks(heartRateStaleRunnable)
        publishBpm(0)
        try {
            if (hasConnectPermission()) {
                gatt.disconnect()
                gatt.close()
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to disconnect after GATT setup failure", error)
        }
        if (!userRequestedDisconnect && lockedDevice.value != null) scheduleReconnect()
    }

    private fun disconnectGatt() {
        handler.removeCallbacks(gattSetupTimeoutRunnable)
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT disconnect permission denied", e)
        } finally {
            handler.removeCallbacks(heartRateStaleRunnable)
            bluetoothGatt = null
        }
    }

    private fun scheduleReconnect() {
        if (reconnectScheduled || userRequestedDisconnect || lockedDevice.value == null) return
        reconnectScheduled = true
        handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    private fun reconnectLockedDevice() {
        if (!hasConnectPermission()) return
        val device = lockedDevice.value ?: return
        if (userRequestedDisconnect || connectionState.value != BleConnectionState.DISCONNECTED) return
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            scheduleReconnect()
            return
        }
        currentAddress = device.address
        connectionState.value = BleConnectionState.CONNECTING
        connectedDevice.value = device.copy(state = DeviceState.CONNECTING)
        updateNotification("Reconnecting to ${device.name}")
        // After a reboot the Bluetooth stack has forgotten the device's address type, so a direct
        // connect to the saved address never completes. Scan first (as a manual reconnect does)
        // and connect using the scanned device; fall back to a direct connect if scanning is unavailable.
        if (!startReconnectScan(adapter, device.address)) {
            connectGattTo(adapter.getRemoteDevice(device.address))
        }
    }

    private fun startReconnectScan(adapter: BluetoothAdapter, address: String): Boolean {
        if (!hasScanPermission()) return false
        val scanner = adapter.bluetoothLeScanner ?: return false
        stopReconnectScan()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (reconnectScanCallback !== this || result.device.address != address) return
                stopReconnectScan()
                if (userRequestedDisconnect || lockedDevice.value?.address != address) return
                connectGattTo(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                if (reconnectScanCallback !== this) return
                Log.w(TAG, "Reconnect scan failed: $errorCode; trying direct connect")
                reconnectScanCallback = null
                handler.removeCallbacks(reconnectScanTimeoutRunnable)
                connectGattTo(adapter.getRemoteDevice(address))
            }
        }
        // Filter on the standard Heart Rate service so the scan keeps running with the screen off;
        // the saved address is matched in the callback so another pilot's device is never picked.
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return try {
            reconnectScanCallback = callback
            scanner.startScan(listOf(filter), settings, callback)
            handler.postDelayed(reconnectScanTimeoutRunnable, RECONNECT_SCAN_TIMEOUT_MS)
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to start reconnect scan", error)
            reconnectScanCallback = null
            false
        }
    }

    private fun stopReconnectScan() {
        handler.removeCallbacks(reconnectScanTimeoutRunnable)
        val callback = reconnectScanCallback ?: return
        reconnectScanCallback = null
        try {
            if (hasScanPermission()) BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(callback)
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to stop reconnect scan", error)
        }
    }

    private fun connectGattTo(device: BluetoothDevice) {
        try {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            handler.removeCallbacks(gattSetupTimeoutRunnable)
            handler.postDelayed(gattSetupTimeoutRunnable, GATT_SETUP_TIMEOUT_MS)
        } catch (e: SecurityException) {
            handler.removeCallbacks(gattSetupTimeoutRunnable)
            connectionState.value = BleConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun saveLockedDevice(device: BluetoothDeviceModel) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putString(KEY_NAME, device.name)
            putString(KEY_ADDRESS, device.address)
        }
        lockedDevice.value = device.copy(state = DeviceState.CONNECTING)
        connectedDevice.value = lockedDevice.value
    }

    private fun loadLockedDevice(): BluetoothDeviceModel? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val address = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
        return BluetoothDeviceModel(
            name = prefs.getString(KEY_NAME, null).orEmpty().ifBlank { "Heart Rate Device" },
            address = address,
            rssi = 0
        )
    }

    private fun clearLockedDevice() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { clear() }
        lockedDevice.value = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Bluetooth",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drone Pilot Tracking")
            .setContentText(text)
            .setSmallIcon(R.drawable.logopst)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun startAsForeground(text: String): Boolean {
        if (!hasConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted; cannot start connectedDevice foreground service")
            return false
        }
        val notification = buildNotification(text)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (error: SecurityException) {
            Log.e(TAG, "Unable to start Bluetooth foreground service", error)
            false
        }
    }

    private fun updateNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

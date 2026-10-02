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
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

        val connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
        val bpm = MutableStateFlow(0)
        val bpmReading = MutableSharedFlow<Int>(extraBufferCapacity = 64)
        val connectedDevice = MutableStateFlow<BluetoothDeviceModel?>(null)
        val lockedDevice = MutableStateFlow<BluetoothDeviceModel?>(null)
        @Volatile private var currentInstance: BluetoothLeService? = null

        fun getRunningService(): BluetoothLeService? = currentInstance

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
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentAddress: String? = null
    private var userRequestedDisconnect = false
    private var reconnectScheduled = false

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
        startAsForeground("Bluetooth standby")
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
        handler.removeCallbacks(reconnectRunnable)
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
        handler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        currentAddress = device.address
        saveLockedDevice(device)
        disconnectGatt()

        connectionState.value = BleConnectionState.CONNECTING
        connectedDevice.value = device.copy(state = DeviceState.CONNECTING)
        try {
            val rawDevice = adapter.getRemoteDevice(device.address)
            bluetoothGatt = rawDevice.connectGatt(this, false, gattCallback)
            updateNotification("Connecting to ${device.name}")
        } catch (e: SecurityException) {
            connectionState.value = BleConnectionState.DISCONNECTED
            connectedDevice.value = null
            updateNotification("Bluetooth permission required")
        }
    }

    fun disconnect() {
        userRequestedDisconnect = true
        handler.removeCallbacks(reconnectRunnable)
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
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "GATT discovery permission denied", e)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
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
            if (status != BluetoothGatt.GATT_SUCCESS) return
            connectionState.value = BleConnectionState.CONNECTED
            connectedDevice.value = connectedDevice.value?.copy(state = DeviceState.CONNECTED)
            updateNotification("Connected to ${connectedDevice.value?.name ?: "device"}")

            val service = gatt.getService(HEART_RATE_SERVICE) ?: return
            val characteristic = service.getCharacteristic(HEART_RATE_CHARACTERISTIC) ?: return
            if (!hasConnectPermission()) return
            try {
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Unable to subscribe to heart rate notifications", e)
            }
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
            publishBpm(effectiveBpm)
        }
    }

    private fun disconnectGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT disconnect permission denied", e)
        } finally {
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
        try {
            bluetoothGatt = adapter.getRemoteDevice(device.address).connectGatt(this, false, gattCallback)
            updateNotification("Reconnecting to ${device.name}")
        } catch (e: SecurityException) {
            connectionState.value = BleConnectionState.DISCONNECTED
            scheduleReconnect()
        }
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
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

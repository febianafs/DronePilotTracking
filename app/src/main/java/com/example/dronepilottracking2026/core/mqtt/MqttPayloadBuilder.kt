package com.example.dronepilottracking2026.core.mqtt

import android.content.Context
import android.provider.Settings
import com.example.dronepilottracking2026.core.bluetooth.BluetoothLeService
import com.example.dronepilottracking2026.data.model.BatteryPayload
import com.example.dronepilottracking2026.data.model.GpsPayload
import com.example.dronepilottracking2026.data.model.IdentityPayload
import com.example.dronepilottracking2026.data.model.RadioHealthPayload
import com.example.dronepilottracking2026.data.model.TrackingPayload
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import com.example.dronepilottracking2026.data.model.LocationData
import org.json.JSONObject

class MqttPayloadBuilder(private val context: Context) {
    fun buildTrackingPayload(
        profile: PersonnelProfile,
        location: LocationData,
        batteryLevel: Int,
        charging: Boolean
    ): String {
        val now = System.currentTimeMillis()
        val heartRateConnected = BluetoothLeService.connectionState.value ==
            com.example.dronepilottracking2026.ui.bluetooth.BleConnectionState.CONNECTED

        val payload = TrackingPayload(
            timestamp = now,
            serialNumber = "",
            androidId = androidId(),
            appVersion = appVersion(),
            identity = IdentityPayload(
                id = profile.nrp,
                nrp = profile.nrp,
                name = profile.name,
                avatarUri = profile.avatarUri.orEmpty()
            ),
            gps = GpsPayload(
                gpsTimestamp = location.timestamp,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracyMeters,
                source = location.source.label,
                satellites = location.satelliteCount
            ),
            radioHealth = RadioHealthPayload(
                heartrateTimestamp = now,
                heartrate = BluetoothLeService.bpm.value,
                connected = heartRateConnected
            ),
            battery = BatteryPayload(
                batteryTimestamp = now,
                level = batteryLevel,
                charging = charging
            )
        )

        return JSONObject().apply {
            put("timestamp", payload.timestamp)
            put("serial_number", payload.serialNumber)
            put("android_id", payload.androidId)
            put("app_version", payload.appVersion)
            put("identity", JSONObject().apply {
                put("id", payload.identity.id)
                put("nrp", payload.identity.nrp)
                put("name", payload.identity.name)
                put("avatar_url", payload.identity.avatarUri)
            })
            put("gps", JSONObject().apply {
                put("gps_timestamp", payload.gps.gpsTimestamp)
                put("latitude", payload.gps.latitude)
                put("longitude", payload.gps.longitude)
                put("accuracy", payload.gps.accuracy)
                put("source", payload.gps.source)
                put("satellites", payload.gps.satellites)
            })
            put("radio_health", JSONObject().apply {
                put("heartrate_timestamp", payload.radioHealth.heartrateTimestamp)
                put("heartrate", payload.radioHealth.heartrate)
                put("connected", payload.radioHealth.connected)
            })
            put("battery", JSONObject().apply {
                put("battery_timestamp", payload.battery.batteryTimestamp)
                put("level", payload.battery.level)
                put("charging", payload.battery.charging)
            })
        }.toString()
    }

    fun buildSosPayload(
        profile: PersonnelProfile,
        location: LocationData,
        sos: Int = 1
    ): String {
        return JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("serial_number", "")
            put("android_id", androidId())
            put("id", profile.nrp)
            put("name", profile.name)
            put("avatar_url", profile.avatarUri.orEmpty())
            put("sos", sos)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracyMeters)
        }.toString()
    }

    private fun androidId(): String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ).orEmpty()

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}

fun batterySnapshot(context: Context): Pair<Int, Boolean> {
    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
        status == android.os.BatteryManager.BATTERY_STATUS_FULL
    return ((level * 100) / scale.coerceAtLeast(1)).coerceIn(0, 100) to charging
}
package com.example.dronepilottracking2026.core.mqtt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.ByteArrayOutputStream
import android.util.Base64

class MqttPayloadBuilder(private val context: Context) {
    private var cachedAvatarUri: String? = null
    private var cachedAvatarBase64: String? = null

    fun buildTrackingPayload(
        profile: PersonnelProfile,
        location: LocationData,
        batteryLevel: Int,
        charging: Boolean,
        serialNumber: String,
        id: String
    ): String {
        val now = System.currentTimeMillis()

        return JSONObject().apply {
            put("source", "INTERNET")
            put("timestamp", now)
            put("serial_number", serialNumber)
            put("android_id", androidId())
            put("identity", JSONObject().apply {
                put("id", id)
                put("avatar", avatarBase64Cached(profile.avatarUri) ?: JSONObject.NULL)
                put("nrp", profile.nrp)
                put("name", profile.name)
            })
            put("gps", JSONObject().apply {
                put("gps_timestamp", location.timestamp)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracyMeters)
            })
            put("radio_health", JSONObject().apply {
                put("heartrate_timestamp", now)
                put("heartrate", BluetoothLeService.bpm.value)
            })
            put("battery", JSONObject().apply {
                put("battery_timestamp", now)
                put("level", batteryLevel)
            })
        }.toString()
    }

    fun buildSosPayload(
        profile: PersonnelProfile,
        location: LocationData,
        sos: Int = 1,
        serialNumber: String,
        id: String
    ): String {
        return JSONObject().apply {
            put("source", "INTERNET")
            put("timestamp", System.currentTimeMillis())
            put("serial_number", serialNumber)
            put("android_id", androidId())
            put("id", id)
            put("nrp", profile.nrp)
            put("name", profile.name)
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

    private fun avatarBase64Cached(uri: String?): String? {
        if (uri == cachedAvatarUri) return cachedAvatarBase64
        val encoded = avatarBase64(uri)
        cachedAvatarUri = uri
        cachedAvatarBase64 = encoded
        return encoded
    }

    private fun avatarBase64(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return runCatching {
            val resolver = context.contentResolver
            val parsedUri = android.net.Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsedUri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val targetSize = 384
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSize) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = resolver.openInputStream(parsedUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            try {
                var quality = 60
                var compressed: ByteArray
                do {
                    compressed = ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                        output.toByteArray()
                    }
                    if (compressed.size <= 60 * 1024 || quality <= 35) break
                    quality -= 5
                } while (quality >= 35)

                Base64.encodeToString(compressed, Base64.NO_WRAP)
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }
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

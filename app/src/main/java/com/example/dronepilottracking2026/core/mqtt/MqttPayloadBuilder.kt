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
import java.io.File
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
        id: String,
        includeAvatar: Boolean = false
    ): String {
        val now = System.currentTimeMillis()
        val heartRate = BluetoothLeService.heartRateForPayload()
        val heartRateConnected = heartRate != null
        val avatarBase64 = if (includeAvatar) {
            avatarBase64Cached(profile.avatarUri)
                ?: throw IllegalStateException("Avatar image could not be encoded")
        } else {
            null
        }

        val payload = TrackingPayload(
            timestamp = now,
            serialNumber = "",
            androidId = androidId(),
            appVersion = appVersion(),
            identity = IdentityPayload(
                id = profile.id,
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
                heartrate = heartRate,
                connected = heartRateConnected
            ),
            battery = BatteryPayload(
                batteryTimestamp = now,
                level = batteryLevel,
                charging = charging
            )
        )

        return JSONObject().apply {
            put("source", "INTERNET")
            put("timestamp", now)
            put("serial_number", serialNumber)
            put("android_id", androidId())
            put("identity", JSONObject().apply {
                put("id", id)
                // Keep avatar out of regular tracking packets so they do not clear
                // the server's stored avatar after the one-time upload.
                if (includeAvatar) put("avatar", avatarBase64)
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
                put("heartrate", heartRate ?: JSONObject.NULL)
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

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun avatarBase64Cached(uri: String?): String? {
        if (uri == cachedAvatarUri) return cachedAvatarBase64
        val encoded = avatarBase64(uri)
        if (encoded != null) {
            cachedAvatarUri = uri
            cachedAvatarBase64 = encoded
        }
        return encoded
    }

    private fun avatarBase64(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return runCatching {
            val parsedUri = android.net.Uri.parse(uri)
            val path = parsedUri.path
            val localFile = path?.let(::File)

            if (parsedUri.scheme == "file" && localFile != null && localFile.extension.equals("jpg", ignoreCase = true)) {
                val optimizedBytes = localFile.readBytes()
                if (optimizedBytes.isNotEmpty()) {
                    return@runCatching Base64.encodeToString(optimizedBytes, Base64.NO_WRAP)
                }
            }

            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsedUri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = resolver.openInputStream(parsedUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            try {
                ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, output)
                    Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                }
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

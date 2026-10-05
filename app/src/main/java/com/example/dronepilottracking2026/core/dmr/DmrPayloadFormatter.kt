package com.example.dronepilottracking2026.core.dmr

import com.example.dronepilottracking2026.data.model.LocationData
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import java.util.Locale

object DmrPayloadFormatter {

    fun data(
        sequence: Long,
        id: String,
        profile: PersonnelProfile,
        location: LocationData,
        heartRate: Int?,
        batteryLevel: Int
    ): String = build(
        type = "D",
        sequence = sequence,
        id = id,
        profile = profile,
        location = location,
        heartRate = heartRate,
        batteryLevel = batteryLevel
    )

    fun sos(
        sequence: Long,
        id: String,
        profile: PersonnelProfile,
        location: LocationData,
        heartRate: Int?,
        batteryLevel: Int
    ): String = build(
        type = "S",
        sequence = sequence,
        id = id,
        profile = profile,
        location = location,
        heartRate = heartRate,
        batteryLevel = batteryLevel
    )

    private fun build(
        type: String,
        sequence: Long,
        id: String,
        profile: PersonnelProfile,
        location: LocationData,
        heartRate: Int?,
        batteryLevel: Int
    ): String {
        val fields = listOf(
            type,
            sequence.toString(),
            clean(id),
            clean(profile.nrp),
            firstName(profile.name),
            formatCoordinate(location.latitude),
            formatCoordinate(location.longitude),
            formatAccuracy(location.accuracyMeters),
            location.timestamp.toString(),
            heartRate?.toString() ?: "-",
            batteryLevel.coerceIn(0, 100).toString()
        )
        return fields.joinToString(separator = "|", postfix = "|")
    }

    private fun firstName(name: String): String =
        name.trim().split(Regex("\\s+")).firstOrNull().orEmpty().let(::clean)

    private fun clean(value: String): String = value
        .replace("|", "")
        .replace("\n", " ")
        .replace("\r", " ")
        .trim()

    private fun formatCoordinate(value: Double): String =
        if (value.isFinite()) String.format(Locale.US, "%.6f", value) else ""

    private fun formatAccuracy(value: Float): String =
        if (value.isFinite() && value >= 0f) String.format(Locale.US, "%.1f", value) else ""
}

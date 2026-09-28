package com.example.dronepilottracking2026.data.model

import android.location.Location

enum class LocationSource(val label: String) {
    NONE("NONE"),
    FUSED("FUSED"),
    FUSED_LAST("FUSED LAST"),
    FUSED_CURRENT("FUSED CURRENT"),
    GPS("GPS"),
    NETWORK("NETWORK"),
    GPS_LAST("GPS LAST"),
    NETWORK_LAST("NETWORK LAST")
}

enum class LocationStatus {
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    SEARCHING,
    ACTIVE,
    STALE,
    ERROR
}

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val source: LocationSource,
    val timestamp: Long = System.currentTimeMillis(),
    val satelliteCount: Int = 0
)

data class LocationUiState(
    val status: LocationStatus = LocationStatus.UNAVAILABLE,
    val location: LocationData? = null,
    val gpsStrength: Int = 0,
    val isMoving: Boolean = false,
    val trackingServiceActive: Boolean = false,
    val error: String? = null
)

fun Location.toLocationData(source: LocationSource, satelliteCount: Int = 0) = LocationData(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    source = source,
    timestamp = time,
    satelliteCount = satelliteCount
)

fun gpsStrength(accuracyMeters: Float): Int = when {
    accuracyMeters <= 10f -> 95
    accuracyMeters <= 20f -> 80
    accuracyMeters <= 50f -> 60
    accuracyMeters <= 100f -> 40
    else -> 20
}

fun LocationData.isStale(now: Long = System.currentTimeMillis()): Boolean =
    now - timestamp > 30_000L

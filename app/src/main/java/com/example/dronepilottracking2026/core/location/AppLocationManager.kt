package com.example.dronepilottracking2026.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.dronepilottracking2026.data.model.LocationData
import com.example.dronepilottracking2026.data.model.LocationSource
import com.example.dronepilottracking2026.data.model.toLocationData
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AppLocationManager(private val context: Context) {
    private val hasPlayServices: Boolean
        get() = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private var intervalMs = 5_000L

    fun setInterval(milliseconds: Long) {
        intervalMs = milliseconds.coerceAtLeast(1_000L)
    }

    fun locationFlow(): Flow<Result<LocationData>> = callbackFlow {
        if (!hasPermission()) {
            trySend(Result.failure(IllegalStateException("Location permission is required")))
            close()
            return@callbackFlow
        }

        val state = RuntimeState()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                state.satellites = (0 until status.satelliteCount).count { status.usedInFix(it) }
            }
        }

        try {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
            trySend(Result.failure(IllegalStateException("GNSS permission denied")))
        }

        val emit = { location: Location, source: LocationSource ->
            trySend(Result.success(location.toLocationData(source, state.satellites)))
        }

        if (hasPlayServices) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellation = CancellationTokenSource()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { emit(it, LocationSource.FUSED) }
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .build()

            try {
                client.lastLocation.addOnSuccessListener { it?.let { location -> emit(location, LocationSource.FUSED_LAST) } }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .addOnSuccessListener { it?.let { location -> emit(location, LocationSource.FUSED_CURRENT) } }
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())

                awaitClose {
                    cancellation.cancel()
                    client.removeLocationUpdates(callback)
                    runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
                }
            } catch (_: SecurityException) {
                trySend(Result.failure(IllegalStateException("Location permission denied")))
                close()
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val source = if (location.provider == LocationManager.GPS_PROVIDER) {
                        LocationSource.GPS
                    } else {
                        LocationSource.NETWORK
                    }
                    emit(location, source)
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }

            try {
                val providers = listOf(
                    LocationManager.GPS_PROVIDER to LocationSource.GPS,
                    LocationManager.NETWORK_PROVIDER to LocationSource.NETWORK
                )
                providers.forEach { (provider, _) ->
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.getLastKnownLocation(provider)?.let { emit(it, if (provider == LocationManager.GPS_PROVIDER) LocationSource.GPS_LAST else LocationSource.NETWORK_LAST) }
                        locationManager.requestLocationUpdates(provider, intervalMs, 1f, listener, Looper.getMainLooper())
                    }
                }

                awaitClose {
                    locationManager.removeUpdates(listener)
                    runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
                }
            } catch (_: SecurityException) {
                trySend(Result.failure(IllegalStateException("Location permission denied")))
                close()
            }
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private class RuntimeState(var satellites: Int = 0)
}

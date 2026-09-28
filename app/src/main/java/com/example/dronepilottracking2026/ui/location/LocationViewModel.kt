package com.example.dronepilottracking2026.ui.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dronepilottracking2026.core.location.LocationTrackingService
import com.example.dronepilottracking2026.data.model.LocationData
import com.example.dronepilottracking2026.data.model.LocationStatus
import com.example.dronepilottracking2026.data.model.LocationUiState
import com.example.dronepilottracking2026.data.model.gpsStrength
import com.example.dronepilottracking2026.data.model.isStale
import com.example.dronepilottracking2026.data.repository.LocationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()
    private var locationJob: Job? = null
    private var serviceLocationJob: Job? = null
    private var sosJob: Job? = null
    private var lastAccepted: LocationData? = null
    private val _sosFeedback = MutableStateFlow<String?>(null)
    val sosFeedback: StateFlow<String?> = _sosFeedback.asStateFlow()

    init {
        observeSosResult()
    }

    fun startLocationUpdates(intervalMs: Long = 5_000L) {
        locationJob?.cancel()
        lastAccepted = null
        _uiState.update { it.copy(status = LocationStatus.SEARCHING, error = null, trackingServiceActive = false) }
        locationJob = viewModelScope.launch {
            repository.locationFlow(intervalMs)
                .catch { error ->
                    _uiState.update { it.copy(status = LocationStatus.ERROR, error = error.message) }
                }
                .collectLatest { result ->
                    result.fold(
                        onSuccess = { location -> acceptLocation(location) },
                        onFailure = { error ->
                            val status = if (error.message?.contains("permission", true) == true) {
                                LocationStatus.PERMISSION_REQUIRED
                            } else {
                                LocationStatus.ERROR
                            }
                            _uiState.update { it.copy(status = status, error = error.message) }
                        }
                    )
                }
        }
    }

    fun startBackgroundTracking() {
        locationJob?.cancel()
        serviceLocationJob?.cancel()
        lastAccepted = null
        LocationTrackingService.start(getApplication<Application>())
        _uiState.update {
            it.copy(status = LocationStatus.SEARCHING, error = null, trackingServiceActive = true)
        }
        serviceLocationJob = viewModelScope.launch {
            LocationTrackingService.locationUpdates.collect { result ->
                result.fold(
                    onSuccess = { acceptLocation(it) },
                    onFailure = { error ->
                        _uiState.update { state ->
                            state.copy(status = LocationStatus.ERROR, error = error.message)
                        }
                    }
                )
            }
        }
    }

    fun stopBackgroundTracking() {
        LocationTrackingService.stop(getApplication<Application>())
        serviceLocationJob?.cancel()
        serviceLocationJob = null
        _uiState.update { it.copy(status = LocationStatus.UNAVAILABLE, trackingServiceActive = false) }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
        _uiState.update { it.copy(status = LocationStatus.UNAVAILABLE, trackingServiceActive = false) }
    }

    fun requestSos() {
        LocationTrackingService.requestSos(getApplication<Application>())
    }

    private fun observeSosResult() {
        sosJob = viewModelScope.launch {
            LocationTrackingService.sosResult.collect { result ->
                _sosFeedback.value = result.fold(
                    onSuccess = { "SOS SENT" },
                    onFailure = { it.message ?: "SOS FAILED" }
                )
            }
        }
    }

    fun clearSosFeedback() {
        _sosFeedback.value = null
    }

    fun refreshStaleState(now: Long = System.currentTimeMillis()) {
        val location = _uiState.value.location ?: return
        if (location.isStale(now)) _uiState.update { it.copy(status = LocationStatus.STALE) }
    }

    private fun acceptLocation(candidate: LocationData) {
        if (candidate.accuracyMeters > 30f) return
        val previous = lastAccepted
        if (previous == null) {
            lastAccepted = candidate
            publish(candidate, false)
            return
        }

        val elapsedSeconds = ((candidate.timestamp - previous.timestamp) / 1_000f).coerceAtLeast(0.5f)
        val distanceMeters = distanceMeters(previous, candidate)
        val moving = distanceMeters / elapsedSeconds > 1.5f || distanceMeters > 8f
        val maxJump = 50f + 5f * elapsedSeconds
        if (distanceMeters > maxJump) return

        val alpha: Double = if (moving) {
            when {
                candidate.accuracyMeters < 5f -> 0.7
                candidate.accuracyMeters < 10f -> 0.5
                else -> 0.3
            }
        } else {
            elapsedSeconds.toDouble() / (5.0 + elapsedSeconds.toDouble())
        }

        val filtered = candidate.copy(
            latitude = previous.latitude + alpha * (candidate.latitude - previous.latitude),
            longitude = previous.longitude + alpha * (candidate.longitude - previous.longitude)
        )
        lastAccepted = filtered
        publish(filtered, moving)
    }

    private fun publish(location: LocationData, moving: Boolean) {
        _uiState.update {
            it.copy(
                status = LocationStatus.ACTIVE,
                location = location,
                gpsStrength = gpsStrength(location.accuracyMeters),
                isMoving = moving
            )
        }
    }

    private fun distanceMeters(a: LocationData, b: LocationData): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            results
        )
        return results[0]
    }

    override fun onCleared() {
        locationJob?.cancel()
        serviceLocationJob?.cancel()
        super.onCleared()
    }
}

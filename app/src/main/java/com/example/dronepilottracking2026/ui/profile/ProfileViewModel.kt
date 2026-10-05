package com.example.dronepilottracking2026.ui.profile

import android.app.Application
import android.net.Uri
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dronepilottracking2026.data.local.ProfileDataStore
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import com.example.dronepilottracking2026.data.model.ProfileEvent
import com.example.dronepilottracking2026.data.model.ProfileUiState
import com.example.dronepilottracking2026.data.model.ProfileLoadState
import com.example.dronepilottracking2026.data.model.validateProfile
import com.example.dronepilottracking2026.data.repository.ProfileRepository
import com.example.dronepilottracking2026.core.location.LocationTrackingService
import com.example.dronepilottracking2026.DronePilotApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProfileRepository(ProfileDataStore(application.applicationContext))


    private val _loadState = MutableStateFlow<ProfileLoadState>(ProfileLoadState.Loading)
    val loadState: StateFlow<ProfileLoadState> = _loadState.asStateFlow()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private var pendingAvatarUri: String? = null

    init {
        viewModelScope.launch {
            repository.profile.collect { profile ->
                val loaded = profile ?: PersonnelProfile()
                _loadState.value = ProfileLoadState.Ready(loaded)
                _uiState.update { state -> state.copy(profile = loaded) }
            }
        }
        val manager = (application as DronePilotApplication).mqttManager
        viewModelScope.launch {
            manager.publishEvents.collect { event ->
                if (event.kind == "AVATAR") {
                    if (event.success) markAvatarSent()
                    else _uiState.update { it.copy(isAvatarSending = false) }
                }
            }
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true, error = null) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, error = null) }
    }

    fun clearNotification() {
        _uiState.update { it.copy(notification = null) }
    }

    fun save(id: String, name: String, nrp: String, avatarUri: String?) {
        val validation = validateProfile(id, name, nrp)
        if (!validation.isValid) {
            _uiState.update { it.copy(error = validation.message) }
            return
        }

        val previousProfile = _uiState.value.profile
        val profile = PersonnelProfile(
            id = id.trim(),
            name = name.trim(),
            nrp = nrp.trim(),
            avatarUri = avatarUri,
            avatarSentUri = if (previousProfile.avatarUri == avatarUri) previousProfile.avatarSentUri else null
        )

        viewModelScope.launch {
            val wasEditing = _uiState.value.isEditing
            _uiState.update { it.copy(isSaving = true, error = null) }
            repository.save(profile)

            if (wasEditing && previousProfile.avatarUri != profile.avatarUri) {
                val oldAvatar = previousProfile.avatarUri
                if (oldAvatar != null && oldAvatar.startsWith("file:")) {
                    runCatching { File(Uri.parse(oldAvatar).path.orEmpty()).delete() }
                }
            }
            if (wasEditing) {
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isSaving = false,
                        notification = "PROFILE UPDATED",
                        error = null
                    )
                }
            } else {
                _uiState.value = ProfileUiState(
                    profile = profile,
                    notification = "PROFILE SAVED"
                )
            }

            val avatarChanged = previousProfile.avatarUri != profile.avatarUri
            if (avatarChanged && !profile.avatarUri.isNullOrBlank()) {
                sendAvatar()
            }
        }
    }

    fun sendAvatar() {
        val current = _uiState.value.profile
        val avatarUri = current.avatarUri ?: return
        if (_uiState.value.isAvatarSending) return
        pendingAvatarUri = avatarUri
        _uiState.update { it.copy(isAvatarSending = true, error = null) }
        LocationTrackingService.requestSendAvatar(getApplication<Application>())
    }

    private fun markAvatarSent() {
        val current = _uiState.value.profile
        val pending = pendingAvatarUri ?: return
        if (current.avatarUri != pending) return
        viewModelScope.launch {
            val updated = current.copy(avatarSentUri = pending)
            repository.save(updated)
            _uiState.update {
                it.copy(profile = updated, isAvatarSending = false, notification = "AVATAR SENT")
            }
            pendingAvatarUri = null
        }
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.Save -> save(event.id, event.name, event.nrp, event.avatarUri)
            ProfileEvent.StartEditing -> startEditing()
            ProfileEvent.CancelEditing -> _uiState.update { it.copy(isEditing = false, error = null) }
            ProfileEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }
}

package com.example.dronepilottracking2026.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dronepilottracking2026.data.local.ProfileDataStore
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import com.example.dronepilottracking2026.data.model.ProfileEvent
import com.example.dronepilottracking2026.data.model.ProfileUiState
import com.example.dronepilottracking2026.data.model.ProfileLoadState
import com.example.dronepilottracking2026.data.model.validateProfile
import com.example.dronepilottracking2026.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProfileRepository(ProfileDataStore(application.applicationContext))

    private val _loadState = MutableStateFlow<ProfileLoadState>(ProfileLoadState.Loading)
    val loadState: StateFlow<ProfileLoadState> = _loadState.asStateFlow()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profile.collect { profile ->
                val loaded = profile ?: PersonnelProfile()
                _loadState.value = ProfileLoadState.Ready(loaded)
                _uiState.update { state -> state.copy(profile = loaded) }
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

    fun save(name: String, nrp: String, avatarUri: String?) {
        val validation = validateProfile(name, nrp)
        if (!validation.isValid) {
            _uiState.update { it.copy(error = validation.message) }
            return
        }

        val profile = PersonnelProfile(
            id = _uiState.value.profile.id,
            name = name.trim(),
            nrp = nrp.trim(),
            avatarUri = avatarUri
        )

        viewModelScope.launch {
            val wasEditing = _uiState.value.isEditing
            _uiState.update { it.copy(isSaving = true, error = null) }
            repository.save(profile)
            _uiState.value = ProfileUiState(
                profile = profile,
                notification = if (wasEditing) "PROFILE UPDATED" else "PROFILE SAVED"
            )
        }
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.Save -> save(event.name, event.nrp, event.avatarUri)
            ProfileEvent.StartEditing -> startEditing()
            ProfileEvent.CancelEditing -> _uiState.update { it.copy(isEditing = false, error = null) }
            ProfileEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }
}

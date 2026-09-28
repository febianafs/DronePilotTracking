package com.example.dronepilottracking2026.data.model

data class PersonnelProfile(
    val name: String = "",
    val nrp: String = "",
    val avatarUri: String? = null
) {
    val isComplete: Boolean
        get() = name.isNotBlank() && nrp.isNotBlank()
}

fun PersonnelProfile?.orEmpty(): PersonnelProfile = this ?: PersonnelProfile()

sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data class Ready(val profile: PersonnelProfile) : ProfileLoadState
}

data class ProfileUiState(
    val profile: PersonnelProfile = PersonnelProfile(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val notification: String? = null
)

sealed interface ProfileEvent {
    data class Save(
        val name: String,
        val nrp: String,
        val avatarUri: String?
    ) : ProfileEvent

    data object StartEditing : ProfileEvent
    data object CancelEditing : ProfileEvent
    data object ClearError : ProfileEvent
}

data class ProfileValidationResult(
    val isValid: Boolean,
    val message: String? = null
)

fun validateProfile(name: String, nrp: String): ProfileValidationResult {
    val normalizedName = name.trim()
    val normalizedNrp = nrp.trim()

    return when {
        normalizedName.isBlank() -> ProfileValidationResult(false, "Nama wajib diisi")
        normalizedNrp.isBlank() -> ProfileValidationResult(false, "NRP wajib diisi")
        normalizedNrp.any { !it.isDigit() } -> ProfileValidationResult(false, "NRP hanya boleh berisi angka")
        else -> ProfileValidationResult(true)
    }
}

fun ProfileUiState.withProfile(profile: PersonnelProfile): ProfileUiState = copy(
    profile = profile,
    isSaving = false,
    error = null
)

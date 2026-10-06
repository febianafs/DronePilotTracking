package com.example.dronepilottracking2026.data.model

data class PersonnelProfile(
    val id: String = "",
    val name: String = "",
    val nrp: String = "",
    val avatarUri: String? = null,
    val avatarSentUri: String? = null
) {
    val isComplete: Boolean
        get() = id.isNotBlank() && name.isNotBlank() && nrp.isNotBlank()
}

const val MAX_PROFILE_ID_LENGTH = 64
const val MAX_PROFILE_NAME_LENGTH = 100
const val MAX_PROFILE_NRP_LENGTH = 64

fun isProfileTextAllowed(value: String): Boolean =
    value.all { it.isLetterOrDigit() || it == ' ' }

fun PersonnelProfile?.orEmpty(): PersonnelProfile = this ?: PersonnelProfile()

sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data class Ready(val profile: PersonnelProfile) : ProfileLoadState
}

data class ProfileUiState(
    val profile: PersonnelProfile = PersonnelProfile(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isAvatarSending: Boolean = false,
    val error: String? = null,
    val notification: String? = null
)

sealed interface ProfileEvent {
    data class Save(
        val id: String,
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

fun validateProfile(id: String, name: String, nrp: String): ProfileValidationResult {
    val normalizedId = id.trim()
    val normalizedName = name.trim()
    val normalizedNrp = nrp.trim()

    return when {
        normalizedId.isBlank() -> ProfileValidationResult(false, "ID wajib diisi")
        normalizedName.isBlank() -> ProfileValidationResult(false, "Nama wajib diisi")
        normalizedNrp.isBlank() -> ProfileValidationResult(false, "NRP wajib diisi")
        normalizedId.length > MAX_PROFILE_ID_LENGTH -> ProfileValidationResult(false, "ID maksimal $MAX_PROFILE_ID_LENGTH karakter")
        normalizedName.length > MAX_PROFILE_NAME_LENGTH -> ProfileValidationResult(false, "Nama maksimal $MAX_PROFILE_NAME_LENGTH karakter")
        normalizedNrp.length > MAX_PROFILE_NRP_LENGTH -> ProfileValidationResult(false, "NRP maksimal $MAX_PROFILE_NRP_LENGTH karakter")
        id.any { it.isWhitespace() } -> ProfileValidationResult(false, "ID tidak boleh mengandung spasi")
        !isProfileTextAllowed(normalizedId) || !isProfileTextAllowed(normalizedName) || !isProfileTextAllowed(normalizedNrp) ->
            ProfileValidationResult(false, "ID, nama, dan NRP hanya boleh berisi huruf, angka, dan spasi")
        else -> ProfileValidationResult(true)
    }
}

fun ProfileUiState.withProfile(profile: PersonnelProfile): ProfileUiState = copy(
    profile = profile,
    isSaving = false,
    error = null
)

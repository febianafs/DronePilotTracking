package com.example.dronepilottracking2026.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.dronepilottracking2026.ui.card.CardProfile

@Composable
fun ProfileSetupCard(
    modifier: Modifier = Modifier,
    initialId: String = "",
    initialName: String = "",
    initialNrp: String = "",
    initialAvatarUri: String? = null,
    onSave: (id: String, name: String, nrp: String, avatarUri: String?) -> Unit
) {
    CardProfile(
        modifier = modifier,
        initialId = initialId,
        initialName = initialName,
        initialNrp = initialNrp,
        initialAvatarUri = initialAvatarUri,
        isEditMode = false,
        showSendAvatar = false,
        onSave = onSave
    )
}

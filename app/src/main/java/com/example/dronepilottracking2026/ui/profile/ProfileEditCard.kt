package com.example.dronepilottracking2026.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.dronepilottracking2026.ui.card.CardProfile

@Composable
fun ProfileEditCard(
    modifier: Modifier = Modifier,
    initialId: String = "",
    initialName: String = "",
    initialNrp: String = "",
    initialAvatarUri: String? = null,
    initialAvatarSentUri: String? = null,
    isAvatarSending: Boolean = false,
    onBack: () -> Unit,
    onSendAvatar: () -> Unit,
    onSave: (id: String, name: String, nrp: String, avatarUri: String?) -> Unit
) {
    CardProfile(
        modifier = modifier,
        initialId = initialId,
        initialName = initialName,
        initialNrp = initialNrp,
        initialAvatarUri = initialAvatarUri,
        initialAvatarSentUri = initialAvatarSentUri,
        isEditMode = true,
        showSendAvatar = true,
        isAvatarSending = isAvatarSending,
        onBack = onBack,
        onSendAvatar = onSendAvatar,
        onSave = onSave
    )
}

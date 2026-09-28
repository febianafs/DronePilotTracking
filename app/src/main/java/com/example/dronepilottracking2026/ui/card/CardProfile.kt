package com.example.dronepilottracking2026.ui.card

import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dronepilottracking2026.ui.theme.DronePilotTracking2026Theme
import com.example.dronepilottracking2026.ui.theme.TacticalAmber
import com.example.dronepilottracking2026.ui.theme.TacticalBackground
import com.example.dronepilottracking2026.ui.theme.TacticalBorder
import com.example.dronepilottracking2026.ui.theme.TacticalButtonText
import com.example.dronepilottracking2026.ui.theme.TacticalCard
import com.example.dronepilottracking2026.ui.theme.TacticalCardElevated
import com.example.dronepilottracking2026.ui.theme.TacticalCyan
import com.example.dronepilottracking2026.ui.theme.TacticalCyanMuted
import com.example.dronepilottracking2026.ui.theme.TacticalMuted
import com.example.dronepilottracking2026.ui.theme.TacticalText

@Composable
fun CardProfile(
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialNrp: String = "",
    initialAvatarUri: String? = null,
    isEditMode: Boolean = false,
    onSave: (name: String, nrp: String, avatarUri: String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var nrp by remember(initialNrp) { mutableStateOf(initialNrp) }
    var avatarUri by remember(initialAvatarUri) { mutableStateOf(initialAvatarUri) }
    var avatarRefreshKey by remember { mutableStateOf(0) }
    var avatarLoading by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            coroutineScope.launch {
                avatarLoading = true
                avatarError = null
                try {
                    val savedUri = withContext(Dispatchers.IO) {
                        val avatarDirectory = File(context.filesDir, "avatars")
                        if (!avatarDirectory.exists() && !avatarDirectory.mkdirs()) {
                            throw IOException("Could not create avatar storage")
                        }
                        val avatarFile = File(avatarDirectory, "avatar-${UUID.randomUUID()}")
                        val input = context.contentResolver.openInputStream(selectedUri)
                            ?: throw IOException("Could not open selected image")
                        input.use { source ->
                            avatarFile.outputStream().use { destination ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var totalBytes = 0L
                                while (true) {
                                    val count = source.read(buffer)
                                    if (count < 0) break
                                    totalBytes += count
                                    if (totalBytes > 15L * 1024L * 1024L) {
                                        throw IOException("Image must be smaller than 15 MB")
                                    }
                                    destination.write(buffer, 0, count)
                                }
                            }
                        }
                        Uri.fromFile(avatarFile).toString()
                    }
                    avatarUri = savedUri
                    avatarRefreshKey++
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    avatarError = error.message ?: "Could not load selected image"
                } finally {
                    avatarLoading = false
                }
            }
        }
    }

    val isFormValid = name.isNotBlank() && nrp.isNotBlank()
    val buttonInteractionSource = remember { MutableInteractionSource() }


    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalCard),
        border = BorderStroke(1.dp, TacticalBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileHeader()

            AvatarPlaceholder(
                name = name,
                avatarUri = avatarUri,
                refreshKey = avatarRefreshKey,
                isLoading = avatarLoading,
                error = avatarError,
                onClick = { avatarPicker.launch(arrayOf("image/*")) },
                onRemove = { avatarUri = null; avatarError = null },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            TacticalTextField(
                value = name,
                onValueChange = { name = it },
                label = "FULL NAME",
                placeholder = "Masukkan nama lengkap",
                leadingIcon = {
                    Text(text = "P", color = TacticalCyan, fontWeight = FontWeight.Bold)
                }
            )

            TacticalTextField(
                value = nrp,
                onValueChange = { nrp = it },
                label = "NRP / PERSONNEL ID",
                placeholder = "Masukkan nomor registrasi",
                keyboardType = KeyboardType.Number,
                leadingIcon = {
                    Text(text = "ID", color = TacticalCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            )

            Button(
                onClick = { onSave(name.trim(), nrp.trim(), avatarUri) },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(6.dp),
                        ambientColor = TacticalCyan.copy(alpha = 0.55f),
                        spotColor = TacticalCyan.copy(alpha = 0.8f)
                    ),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalCyan,
                    contentColor = TacticalButtonText,
                    disabledContainerColor = TacticalCardElevated,
                    disabledContentColor = TacticalMuted
                ),
                interactionSource = buttonInteractionSource,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 12.dp
                )
            ) {
                Text(
                    text = if (isEditMode) "UPDATE PERSONNEL PROFILE" else "SAVE PERSONNEL PROFILE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Text(
                text = "DATA STORED ON THIS DEVICE  •  NO NETWORK REQUIRED",
                modifier = Modifier.fillMaxWidth(),
                color = TacticalMuted,
                fontSize = 10.sp,
                letterSpacing = 0.7.sp
            )
        }
    }
}

@Composable
private fun ProfileHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "PERSONEL IDENTIFICATION",
                color = TacticalText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PROFILE SETUP",
                color = TacticalMuted,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(TacticalCardElevated)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(TacticalCyan)
            )
            Text(
                text = "LOCAL",
                color = TacticalMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun AvatarPlaceholder(
    name: String,
    avatarUri: String?,
    refreshKey: Int,
    isLoading: Boolean,
    error: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = name
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
    var avatarLoadFailed by remember(avatarUri) { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(TacticalCardElevated)
                .border(1.dp, TacticalCyanMuted, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null && !avatarLoadFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUri)
                        .memoryCacheKey("profile-avatar:$avatarUri:$refreshKey")
                        .diskCacheKey("profile-avatar:$avatarUri:$refreshKey")
                        .build(),
                    contentDescription = "Personnel avatar",
                    modifier = Modifier.size(104.dp),
                    contentScale = ContentScale.Crop,
                    onError = { avatarLoadFailed = true }
                )
            } else if (initials.isEmpty()) {
                Text(text = "+", color = TacticalCyan, fontSize = 32.sp, fontWeight = FontWeight.Light)
            } else {
                Text(
                    text = initials,
                    color = TacticalCyan,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
        Text(
            text = when {
                isLoading -> "LOADING AVATAR..."
                avatarUri == null -> "ADD AVATAR  •  OPTIONAL"
                else -> "CHANGE AVATAR"
            },
            color = TacticalMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.clickable(enabled = !isLoading, onClick = onClick)
        )
        if (avatarUri != null && !isLoading) {
            Text(
                text = "REMOVE AVATAR",
                color = TacticalAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onRemove)
            )
        }
        if (error != null) {
            Text(text = error, color = com.example.dronepilottracking2026.ui.theme.TacticalRed, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TacticalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = label,
            color = TacticalMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(text = placeholder, color = TacticalMuted.copy(alpha = 0.7f))
            },
            leadingIcon = leadingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TacticalCardElevated,
                unfocusedContainerColor = TacticalCardElevated,
                focusedBorderColor = TacticalCyan,
                unfocusedBorderColor = TacticalBorder,
                focusedTextColor = TacticalText,
                unfocusedTextColor = TacticalText,
                focusedLeadingIconColor = TacticalCyan,
                unfocusedLeadingIconColor = TacticalMuted,
                cursorColor = TacticalCyan
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020B18)
@Composable
private fun CardProfilePreview() {
    DronePilotTracking2026Theme(dynamicColor = false, darkTheme = true) {
        Box(
            modifier = Modifier
                .background(TacticalBackground)
                .padding(16.dp)
        ) {
            CardProfile()
        }
    }
}

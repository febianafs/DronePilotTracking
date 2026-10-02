package com.example.dronepilottracking2026.ui.card

import android.net.Uri
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.ByteArrayOutputStream
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
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
    initialAvatarSentUri: String? = null,
    isEditMode: Boolean = false,
    isAvatarSending: Boolean = false,
    onBack: () -> Unit = {},
    onSendAvatar: () -> Unit = {},
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
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            coroutineScope.launch {
                avatarLoading = true
                avatarError = null
                try {
                    val savedUri = withContext(Dispatchers.IO) {
                        val avatarDirectory = File(context.filesDir, "avatars")
                        if (!avatarDirectory.exists() && !avatarDirectory.mkdirs()) {
                            throw IOException("Could not create avatar storage")
                        }
                            val avatarFile = File(avatarDirectory, "avatar-${UUID.randomUUID()}.jpg")
                            val sourceFile = File(avatarDirectory, "source-${UUID.randomUUID()}")
                            try {
                                context.contentResolver.openInputStream(selectedUri)?.use { source ->
                                    sourceFile.outputStream().use { destination ->
                                        source.copyTo(destination)
                                    }
                                } ?: throw IOException("Could not open selected image")

                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
                            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                                throw IOException("Selected file is not a valid image")
                            }

                            val targetSize = 256
                            var sample = 1
                            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSize) {
                                sample *= 2
                            }
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
                                ?: throw IOException("Could not decode selected image")

                            try {
                                var quality = 45
                                var compressed: ByteArray
                                do {
                                    compressed = ByteArrayOutputStream().use { output ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                                        output.toByteArray()
                                    }
                                    if (compressed.size <= 25 * 1024 || quality <= 30) break
                                    quality -= 5
                                } while (quality >= 30)

                                if (compressed.isEmpty()) {
                                    throw IOException("Could not compress selected image")
                                }
                                avatarFile.writeBytes(compressed)
                            } finally {
                                bitmap.recycle()
                            }
                            Uri.fromFile(avatarFile).toString()
                            } finally {
                                sourceFile.delete()
                            }
                        }
                        val previousCandidate = avatarUri
                        if (previousCandidate != null &&
                            previousCandidate != initialAvatarUri &&
                            previousCandidate.startsWith("file:")
                        ) {
                            runCatching {
                                File(Uri.parse(previousCandidate).path.orEmpty()).delete()
                            }
                        }
                        avatarUri = savedUri
                        avatarRefreshKey++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        avatarError = error.message ?: "Could not process selected image"
                    } finally {
                        avatarLoading = false
                    }
                }
            }
        }

    val isFormValid = name.isNotBlank() && nrp.isNotBlank()
    val hasChanges = !isEditMode ||
        name.trim() != initialName.trim() ||
        nrp.trim() != initialNrp.trim() ||
        avatarUri != initialAvatarUri
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
            if (isEditMode) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
                        val strokeWidth = 2.dp.toPx()
                        val centerY = size.height / 2f
                        val left = 1.dp.toPx()
                        val right = size.width - 1.dp.toPx()
                        val head = 5.dp.toPx()
                        drawLine(
                            color = TacticalCyan,
                            start = androidx.compose.ui.geometry.Offset(left, centerY),
                            end = androidx.compose.ui.geometry.Offset(right, centerY),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = TacticalCyan,
                            start = androidx.compose.ui.geometry.Offset(left, centerY),
                            end = androidx.compose.ui.geometry.Offset(left + head, centerY - head),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = TacticalCyan,
                            start = androidx.compose.ui.geometry.Offset(left, centerY),
                            end = androidx.compose.ui.geometry.Offset(left + head, centerY + head),
                            strokeWidth = strokeWidth
                        )
                    }
                    Text(
                        text = "Back",
                        color = TacticalCyan,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            ProfileHeader()

            AvatarPlaceholder(
                name = name,
                avatarUri = avatarUri,
                refreshKey = avatarRefreshKey,
                isLoading = avatarLoading,
                error = avatarError,
                onClick = { avatarPicker.launch(arrayOf("image/*")) },
                onRemove = {
                    val currentCandidate = avatarUri
                    if (currentCandidate != null &&
                        currentCandidate != initialAvatarUri &&
                        currentCandidate.startsWith("file:")
                    ) {
                        runCatching { File(Uri.parse(currentCandidate).path.orEmpty()).delete() }
                    }
                    avatarUri = null
                    avatarError = null
                },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSave(name.trim(), nrp.trim(), avatarUri) },
                    enabled = isFormValid && hasChanges && !avatarLoading,
                    modifier = Modifier
                        .weight(1f)
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
                        text = if (isEditMode) "UPDATE PROFILE" else "SAVE PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Button(
                    onClick = onSendAvatar,
                    enabled = isEditMode &&
                        !isAvatarSending &&
                        !initialAvatarUri.isNullOrBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = TacticalCardElevated,
                        disabledContentColor = TacticalMuted
                    )
                ) {
                    Text(
                        text = if (isAvatarSending) "SENDING..." else "SEND AVATAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
        Text(
            text = "IMAGE MAX 15 MB",
            color = TacticalMuted,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp
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

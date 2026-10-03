package com.example.dronepilottracking2026.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dronepilottracking2026.ui.theme.DronePilotTracking2026Theme
import com.example.dronepilottracking2026.ui.theme.TacticalAmber
import com.example.dronepilottracking2026.ui.theme.TacticalBackground
import com.example.dronepilottracking2026.ui.theme.TacticalBorder
import com.example.dronepilottracking2026.ui.theme.TacticalButtonText
import com.example.dronepilottracking2026.ui.theme.TacticalCard
import com.example.dronepilottracking2026.ui.theme.TacticalCardElevated
import com.example.dronepilottracking2026.ui.theme.TacticalCyan
import com.example.dronepilottracking2026.ui.theme.TacticalMuted
import com.example.dronepilottracking2026.ui.theme.TacticalText
import com.example.dronepilottracking2026.ui.location.LocationStatusCard
import com.example.dronepilottracking2026.ui.location.LocationViewModel


@Composable
fun TrackingStatusScreen(
    id: String,
    name: String,
    nrp: String,
    avatarUri: String? = null,
    onEditProfile: () -> Unit,
    locationViewModel: LocationViewModel? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader()

        ProfileIdentityCard(
            id = id,
            name = name,
            nrp = nrp,
            avatarUri = avatarUri,
            onEditProfile = onEditProfile
        )

        locationViewModel?.let { LocationStatusCard(viewModel = it) }

        Spacer(modifier = Modifier.weight(1f))

    }
}


@Composable
private fun ScreenHeader() {
    Column {
        Text(
            text = "DRONE PILOT TRACKING",
            color = TacticalText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "PERSONNEL STATUS",
            color = TacticalMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
    }
}

@Composable
private fun ProfileIdentityCard(
    id: String,
    name: String,
    nrp: String,
    avatarUri: String?,
    onEditProfile: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TacticalCard)
            .border(1.dp, TacticalBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "PROFILE",
                    color = TacticalText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                SectionLabel(text = "ACTIVE PERSONNEL")
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(TacticalCyan.copy(alpha = 0.14f))
                    .clickable(onClick = onEditProfile)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EDIT",
                    color = TacticalCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(TacticalCardElevated)
                    .border(1.dp, TacticalCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = "Personnel avatar",
                        modifier = Modifier.size(68.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initialsOf(name),
                        color = TacticalAmber,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = name,
                    color = TacticalText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID   $id",
                    color = TacticalMuted,
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "NRP  $nrp",
                    color = TacticalMuted,
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TacticalMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

private fun initialsOf(name: String): String {
    val initials = name
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    return initials.ifBlank { "P" }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B110D)
@Composable
private fun TrackingStatusScreenPreview() {
    DronePilotTracking2026Theme(dynamicColor = false, darkTheme = true) {
        TrackingStatusScreen(
            id = "001",
            name = "Febi",
            nrp = "123456",
            avatarUri = null,
            onEditProfile = {}
        )
    }
}

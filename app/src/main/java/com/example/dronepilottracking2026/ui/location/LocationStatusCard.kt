package com.example.dronepilottracking2026.ui.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dronepilottracking2026.data.model.LocationStatus
import com.example.dronepilottracking2026.data.model.LocationUiState
import com.example.dronepilottracking2026.data.model.DeliveryMode
import com.example.dronepilottracking2026.ui.theme.TacticalAmber
import com.example.dronepilottracking2026.ui.theme.TacticalBorder
import com.example.dronepilottracking2026.ui.theme.TacticalButtonText
import com.example.dronepilottracking2026.ui.theme.TacticalCard
import com.example.dronepilottracking2026.ui.theme.TacticalCardElevated
import com.example.dronepilottracking2026.ui.theme.TacticalCyan
import com.example.dronepilottracking2026.ui.theme.TacticalGreen
import com.example.dronepilottracking2026.ui.theme.TacticalMuted
import com.example.dronepilottracking2026.ui.theme.TacticalRed
import com.example.dronepilottracking2026.ui.theme.TacticalText

@Composable
fun LocationStatusCard(
    viewModel: LocationViewModel,
    deliveryMode: DeliveryMode = DeliveryMode.INTERNET,
    dmrReady: Boolean = true,
    onOpenDmr: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.startBackgroundTracking()
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshBackgroundPermission() }

    LaunchedEffect(deliveryMode, dmrReady) {
        if (deliveryMode == DeliveryMode.DMR && !dmrReady) {
            viewModel.stopBackgroundTracking()
            return@LaunchedEffect
        }
        if (hasLocationPermission(context)) {
            viewModel.startBackgroundTracking()
        } else {
            foregroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(state.trackingServiceActive) {
        if (state.trackingServiceActive) viewModel.refreshBackgroundPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBackgroundPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LocationStatusCardContent(
        state = state,
        deliveryMode = deliveryMode,
        dmrReady = dmrReady,
        onOpenDmr = onOpenDmr,
        modifier = modifier,
        onEnableBackground = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.startActivity(applicationDetailsIntent(context))
                } else {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else {
                context.startActivity(applicationDetailsIntent(context))
            }
        }
    )
}

@Composable
private fun LocationStatusCardContent(
    state: LocationUiState,
    deliveryMode: DeliveryMode,
    dmrReady: Boolean,
    onOpenDmr: () -> Unit,
    modifier: Modifier = Modifier,
    onEnableBackground: () -> Unit
) {
    val statusColor = when (state.status) {
        LocationStatus.ACTIVE -> TacticalGreen
        LocationStatus.SEARCHING, LocationStatus.PERMISSION_REQUIRED, LocationStatus.STALE -> TacticalAmber
        LocationStatus.ERROR -> TacticalRed
        LocationStatus.UNAVAILABLE -> TacticalMuted
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TacticalCard)
            .border(1.dp, TacticalBorder, RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (deliveryMode == DeliveryMode.DMR && !dmrReady) {
            Text(
                "DMR mode is selected. Open DMR Walkie Talkie before location tracking starts.",
                color = TacticalMuted,
                fontSize = 11.sp
            )
            Button(
                onClick = onOpenDmr,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalCyan,
                    contentColor = TacticalButtonText
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("OPEN DMR APP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("LOCATION STATUS", color = TacticalText, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(3.dp))
                Text("CONTINUOUS TRACKING", color = TacticalMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            StatusBadge(state.status.label(), statusColor)
        }

        if (state.location != null) {
            LocationValueRow("LATITUDE", "%.6f".format(state.location.latitude))
            LocationValueRow("LONGITUDE", "%.6f".format(state.location.longitude))
        } else {
            Text(state.error ?: "Location data is not available", color = TacticalMuted, fontSize = 12.sp)
        }

        if (state.trackingServiceActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (state.backgroundLocationGranted) {
                Text(
                    "Continuous tracking is active. Background location permission is enabled.",
                    color = TacticalMuted,
                    fontSize = 10.sp
                )
            } else {
                Text(
                    "Tracking remains active while the app is visible. Grant background location permission to support tracking when the app is not visible.",
                    color = TacticalMuted,
                    fontSize = 10.sp
                )
                Button(
                    onClick = onEnableBackground,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TacticalCardElevated,
                        contentColor = TacticalCyan
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("ENABLE BACKGROUND LOCATION", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LocationValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TacticalCardElevated)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TacticalMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(value, color = TacticalText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

private fun LocationStatus.label(): String = when (this) {
    LocationStatus.ACTIVE -> "GPS ACTIVE"
    LocationStatus.SEARCHING -> "SEARCHING"
    LocationStatus.PERMISSION_REQUIRED -> "PERMISSION REQUIRED"
    LocationStatus.STALE -> "STALE DATA"
    LocationStatus.ERROR -> "GPS ERROR"
    LocationStatus.UNAVAILABLE -> "UNAVAILABLE"
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun applicationDetailsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.parse("package:${context.packageName}")
)

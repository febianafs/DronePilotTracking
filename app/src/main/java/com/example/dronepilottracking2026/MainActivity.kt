package com.example.dronepilottracking2026

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dronepilottracking2026.data.model.ProfileLoadState
import com.example.dronepilottracking2026.ui.bluetooth.BluetoothViewModel
import com.example.dronepilottracking2026.ui.card.CardProfile
import com.example.dronepilottracking2026.ui.navigation.AppBottomNavigation
import com.example.dronepilottracking2026.ui.navigation.AppDestination
import com.example.dronepilottracking2026.ui.profile.ProfileViewModel
import com.example.dronepilottracking2026.ui.location.LocationViewModel
import com.example.dronepilottracking2026.ui.mqtt.MqttViewModel
import com.example.dronepilottracking2026.ui.screen.HeartRateScreen
import com.example.dronepilottracking2026.ui.screen.MqttSettingsScreen
import com.example.dronepilottracking2026.ui.screen.TrackingStatusScreen
import com.example.dronepilottracking2026.core.location.LocationTrackingService
import com.example.dronepilottracking2026.ui.sos.GlobalSosStatusBar
import com.example.dronepilottracking2026.ui.theme.DronePilotTracking2026Theme

private val TacticalBackground = Color(0xFF020B18)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DronePilotTracking2026Theme(
                darkTheme = true,
                dynamicColor = false
            ) {
                DronePilotApp()
            }
        }
    }
}

@Composable
private fun DronePilotApp() {
    val profileViewModel: ProfileViewModel = viewModel()
    val bluetoothViewModel: BluetoothViewModel = viewModel()
    val locationViewModel: LocationViewModel = viewModel()
    val mqttViewModel: MqttViewModel = viewModel()
    val context = LocalContext.current
    val sosActive by LocationTrackingService.sosActive.collectAsStateWithLifecycle()
    val profileLoadState by profileViewModel.loadState.collectAsStateWithLifecycle()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    var currentDestinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    val currentDestination = AppDestination.valueOf(currentDestinationName)

    when (val loadState = profileLoadState) {
        ProfileLoadState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TacticalBackground)
            )
        }

        is ProfileLoadState.Ready -> {
            val profile = loadState.profile
            val showProfileForm = !profile.isComplete || profileUiState.isEditing

            if (showProfileForm) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TacticalBackground)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    CardProfile(
                        initialName = profile.name,
                        initialNrp = profile.nrp,
                        initialAvatarUri = profile.avatarUri,
                        isEditMode = profile.isComplete,
                        onSave = profileViewModel::save
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TacticalBackground)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GlobalSosStatusBar(
                            sosActive = sosActive,
                            onSos = { LocationTrackingService.requestSos(context) },
                            onClear = { LocationTrackingService.clearSos(context) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentDestination) {
                            AppDestination.HOME -> TrackingStatusScreen(
                                name = profile.name,
                                nrp = profile.nrp,
                                avatarUri = profile.avatarUri,
                                onEditProfile = profileViewModel::startEditing,
                                locationViewModel = locationViewModel
                            )

                            AppDestination.HEART_RATE -> HeartRateScreen(viewModel = bluetoothViewModel)
                            AppDestination.MQTT_SETTINGS -> MqttSettingsScreen(viewModel = mqttViewModel)
                            }
                        }
                    }

                    AppBottomNavigation(
                        currentDestination = currentDestination,
                        onDestinationSelected = { currentDestinationName = it.name },
                        sosActive = sosActive,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF020B18)
@Composable
private fun DronePilotAppPreview() {
    DronePilotTracking2026Theme(darkTheme = true, dynamicColor = false) {
        DronePilotApp()
    }
}
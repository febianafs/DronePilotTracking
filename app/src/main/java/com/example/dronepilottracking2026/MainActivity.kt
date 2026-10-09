package com.example.dronepilottracking2026

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dronepilottracking2026.data.model.ProfileLoadState
import com.example.dronepilottracking2026.ui.bluetooth.BluetoothViewModel
import com.example.dronepilottracking2026.ui.card.CardProfile
import com.example.dronepilottracking2026.ui.navigation.AppBottomNavigation
import com.example.dronepilottracking2026.ui.navigation.AppDestination
import com.example.dronepilottracking2026.ui.profile.ProfileViewModel
import com.example.dronepilottracking2026.ui.profile.ProfileSetupCard
import com.example.dronepilottracking2026.ui.profile.ProfileEditCard
import com.example.dronepilottracking2026.ui.location.LocationViewModel
import com.example.dronepilottracking2026.ui.mqtt.MqttViewModel
import com.example.dronepilottracking2026.ui.screen.HeartRateScreen
import com.example.dronepilottracking2026.ui.screen.MqttSettingsScreen
import com.example.dronepilottracking2026.ui.screen.TrackingStatusScreen
import com.example.dronepilottracking2026.core.bluetooth.BluetoothLeService
import com.example.dronepilottracking2026.core.location.LocationTrackingService
import com.example.dronepilottracking2026.ui.network.ConnectivityBanner
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val sosActive by LocationTrackingService.sosActive.collectAsStateWithLifecycle()
    val profileLoadState by profileViewModel.loadState.collectAsStateWithLifecycle()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val mqttUiState by mqttViewModel.uiState.collectAsStateWithLifecycle()
    val connectivityIssue by mqttViewModel.connectivityIssue.collectAsStateWithLifecycle()

    // Reconnect a previously paired heart rate device as soon as the app opens, not only
    // when the Heart Rate tab is visited (e.g. after a reboot or force stop).
    LaunchedEffect(Unit) {
        if (BluetoothLeService.hasLockedDevice(context)) bluetoothViewModel.ensureService()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mqttViewModel.refreshDmrReadiness()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                BackHandler(enabled = profile.isComplete && profileUiState.isEditing) {
                    profileViewModel.cancelEditing()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TacticalBackground)
                        .statusBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (profile.isComplete) {
                        ProfileEditCard(
                            initialId = profile.id,
                            initialName = profile.name,
                            initialNrp = profile.nrp,
                            initialAvatarUri = profile.avatarUri,
                            initialAvatarSentUri = profile.avatarSentUri,
                            isAvatarSending = profileUiState.isAvatarSending,
                            onBack = profileViewModel::cancelEditing,
                            onSendAvatar = profileViewModel::sendAvatar,
                            onSave = profileViewModel::save
                        )
                    } else {
                        ProfileSetupCard(
                            initialId = profile.id,
                            initialName = profile.name,
                            initialNrp = profile.nrp,
                            initialAvatarUri = profile.avatarUri,
                            onSave = profileViewModel::save
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TacticalBackground)
                        .statusBarsPadding()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GlobalSosStatusBar(
                            sosActive = sosActive,
                            onSos = { LocationTrackingService.requestSos(context) },
                            onClear = { LocationTrackingService.clearSos(context) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        connectivityIssue?.let { issue ->
                            ConnectivityBanner(
                                issue = issue,
                                sosActive = sosActive,
                                onClick = { currentDestinationName = AppDestination.MQTT_SETTINGS.name },
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentDestination) {
                            AppDestination.HOME -> TrackingStatusScreen(
                                id = profile.id,
                                name = profile.name,
                                nrp = profile.nrp,
                                avatarUri = profile.avatarUri,
                                onEditProfile = profileViewModel::startEditing,
                                locationViewModel = locationViewModel,
                                deliveryMode = mqttUiState.deliveryMode,
                                dmrReady = mqttUiState.dmrReadiness.isReady,
                                onOpenDmr = mqttViewModel::openDmrApp
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
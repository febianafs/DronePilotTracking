package com.example.dronepilottracking2026.ui.screen

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dronepilottracking2026.ui.theme.TacticalAmber
import com.example.dronepilottracking2026.ui.theme.TacticalBackground
import com.example.dronepilottracking2026.ui.theme.TacticalBorder
import com.example.dronepilottracking2026.ui.theme.TacticalCard
import com.example.dronepilottracking2026.ui.theme.TacticalCardElevated
import com.example.dronepilottracking2026.ui.theme.TacticalCyan
import com.example.dronepilottracking2026.ui.theme.TacticalGreen
import com.example.dronepilottracking2026.ui.theme.TacticalMuted
import com.example.dronepilottracking2026.ui.theme.TacticalText
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dronepilottracking2026.ui.bluetooth.BleConnectionState
import com.example.dronepilottracking2026.ui.bluetooth.BluetoothDeviceModel
import com.example.dronepilottracking2026.ui.bluetooth.BluetoothViewModel
import com.example.dronepilottracking2026.ui.bluetooth.DeviceState
import com.example.dronepilottracking2026.ui.bluetooth.HeartRateUiState
import com.example.dronepilottracking2026.ui.bluetooth.heartStatus


@Composable
fun HeartRateScreen(viewModel: BluetoothViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshBluetoothState()
        viewModel.ensureService()
        if (it.values.all { granted -> granted }) viewModel.startScan()
    }

    LaunchedEffect(Unit) {
        viewModel.ensureService()
        viewModel.refreshBluetoothState()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(TacticalBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle("HEART RATE", "BLUETOOTH HEART RATE MONITOR")
        }
        item {
            BluetoothControlCard(
                state = state,
                onEnable = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    ) permissionLauncher.launch(bluetoothPermissions())
                    else context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onScan = {
                    if (hasPermission(context)) viewModel.startScan() else permissionLauncher.launch(bluetoothPermissions())
                },
                onStopScan = viewModel::stopScan
            )
        }
        item { HeartRateValueCard(state) }
        state.connectedDevice?.let { device ->
            item { ConnectedDeviceCard(device, state, viewModel::forgetDevice) }
        }
        item {
            Text("AVAILABLE DEVICES", color = TacticalMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        if (state.devices.isEmpty()) {
            item { EmptyDeviceCard(state) }
        } else {
            items(state.devices, key = { it.address }) { device ->
                DeviceRow(device = device, onConnect = { viewModel.connect(device) })
            }
        }
        state.error?.let { message ->
            item {
                Text(message, color = Color(0xFFE08282), fontSize = 12.sp, modifier = Modifier.clickable { viewModel.clearError() })
            }
        }
        item {
            Text("BLE connection stays active through a foreground service", color = TacticalMuted, fontSize = 10.sp, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun BluetoothControlCard(state: HeartRateUiState, onEnable: () -> Unit, onScan: () -> Unit, onStopScan: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TacticalCard).border(1.dp, TacticalBorder, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BLUETOOTH DEVICE", color = TacticalMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.bluetoothEnabled) TacticalGreen else TacticalMuted))
            Column(Modifier.weight(1f)) {
                Text(if (state.bluetoothEnabled) "ACTIVE" else "INACTIVE", color = TacticalText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (state.isScanning) "Searching for BLE devices..." else "Standard Heart Rate Profile", color = TacticalMuted, fontSize = 12.sp)
            }
            Button(onClick = if (state.bluetoothEnabled) { onScan } else onEnable, colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan, contentColor = com.example.dronepilottracking2026.ui.theme.TacticalButtonText)) {
                Text(if (state.bluetoothEnabled) "SCAN" else "ENABLE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (state.isScanning) Button(onClick = onStopScan, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TacticalCardElevated, contentColor = TacticalText)) { Text("STOP SCAN") }
    }
}

@Composable
private fun HeartRateValueCard(state: HeartRateUiState) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TacticalCard).border(1.dp, TacticalBorder, RoundedCornerShape(18.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CURRENT HEART RATE", color = TacticalMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(if (state.bpm == 0) "--" else state.bpm.toString(), color = TacticalAmber, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text("BPM  •  ${state.heartStatus().uppercase()}", color = TacticalText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun ConnectedDeviceCard(device: BluetoothDeviceModel, state: HeartRateUiState, onForget: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TacticalCard).border(1.dp, TacticalGreen, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("CONNECTED DEVICE", color = TacticalGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        Text(device.name, color = TacticalText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(device.address, color = TacticalMuted, fontSize = 12.sp)
        Text(if (state.connectionState == BleConnectionState.CONNECTED) "Connected" else "Reconnecting", color = TacticalGreen, fontSize = 12.sp)
        Button(onClick = onForget, colors = ButtonDefaults.buttonColors(containerColor = TacticalCardElevated, contentColor = TacticalText)) { Text("FORGET DEVICE") }
    }
}

@Composable
private fun DeviceRow(device: BluetoothDeviceModel, onConnect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TacticalCard).border(1.dp, TacticalBorder, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(device.name, color = TacticalText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${device.address}  •  RSSI ${device.rssi}", color = TacticalMuted, fontSize = 11.sp)
            if (device.state == DeviceState.CONNECTING) Text("Connecting...", color = TacticalAmber, fontSize = 11.sp)
        }
        Button(onClick = onConnect, enabled = device.state != DeviceState.CONNECTING && device.state != DeviceState.CONNECTED, colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan, contentColor = com.example.dronepilottracking2026.ui.theme.TacticalButtonText)) { Text(if (device.state == DeviceState.CONNECTED) "CONNECTED" else "CONNECT", fontSize = 10.sp) }
    }
}

@Composable
private fun EmptyDeviceCard(state: HeartRateUiState) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TacticalCard).border(1.dp, TacticalBorder, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(if (state.isScanning) "SEARCHING FOR DEVICES..." else "NO BLE DEVICE FOUND", color = TacticalText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Use Scan to search for a compatible heart rate sensor", color = TacticalMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column { Text(title, color = TacticalText, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(5.dp)); Text(subtitle, color = TacticalMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
}

private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
private fun hasPermission(context: android.content.Context): Boolean = bluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

package com.example.dronepilottracking2026.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.example.dronepilottracking2026.data.model.MqttConnectionState

import com.example.dronepilottracking2026.data.model.DeliveryMode
import com.example.dronepilottracking2026.core.dmr.DmrReadiness
import com.example.dronepilottracking2026.core.dmr.DmrSendStatus
import com.example.dronepilottracking2026.ui.mqtt.MqttViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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


@Composable
fun MqttSettingsScreen(viewModel: MqttViewModel? = null, modifier: Modifier = Modifier) {
    val mqttState by (viewModel?.uiState ?: kotlinx.coroutines.flow.MutableStateFlow(com.example.dronepilottracking2026.data.model.MqttUiState())).collectAsStateWithLifecycle()
    val savedConfig = mqttState.config
    val deliveryMode = mqttState.deliveryMode
    var server by remember(savedConfig.host) { mutableStateOf(savedConfig.host) }
    var tcpPort by remember(savedConfig.tcpPort) { mutableStateOf(savedConfig.tcpPort?.toString().orEmpty()) }
    var wsPort by remember(savedConfig.wsPort) { mutableStateOf(savedConfig.wsPort?.toString().orEmpty()) }
    var username by remember(savedConfig.username) { mutableStateOf(savedConfig.username) }
    var password by remember(savedConfig.password) { mutableStateOf(savedConfig.password) }
    var serialNumber by remember(savedConfig.serialNumber) { mutableStateOf(savedConfig.serialNumber) }
    var id by remember(savedConfig.id) { mutableStateOf(savedConfig.id) }
    var useWebSocket by remember(savedConfig.useWebSocket) { mutableStateOf(savedConfig.useWebSocket) }
    var personelDataTopic by remember(savedConfig.personelDataTopic) { mutableStateOf(savedConfig.personelDataTopic) }
    var personelSosTopic by remember(savedConfig.personelSosTopic) { mutableStateOf(savedConfig.personelSosTopic) }
    var interval by remember(savedConfig.intervalMs) { mutableStateOf(savedConfig.intervalMs?.toIntervalLabel().orEmpty()) }
    var pendingMode by remember { mutableStateOf<DeliveryMode?>(null) }
    var showNotificationAccessDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(deliveryMode) {
        viewModel?.refreshDmrReadiness()
    }

    LaunchedEffect(deliveryMode, mqttState.dmrNotificationAccessGranted) {
        showNotificationAccessDialog = deliveryMode == DeliveryMode.DMR &&
            !mqttState.dmrNotificationAccessGranted
    }

    DisposableEffect(lifecycleOwner, deliveryMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel?.refreshDmrReadiness()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedPort = (if (useWebSocket) wsPort else tcpPort).toIntOrNull()
    val connectionReady = server.isNotBlank() &&
        selectedPort?.let { it in 1..65535 } == true &&
        username.isNotBlank() &&
        password.isNotBlank()
    val topicsReady = personelDataTopic.isNotBlank() && personelSosTopic.isNotBlank()
    val formReady = connectionReady && topicsReady && interval.toIntervalMs() != null
    val currentConfig = {
        MqttConfig(
            host = server.trim(),
            tcpPort = tcpPort.toIntOrNull(),
            wsPort = wsPort.toIntOrNull(),
            username = username.trim(),
            password = password,
            serialNumber = serialNumber.trim(),
            id = id.trim(),
            useWebSocket = useWebSocket,
            personelDataTopic = personelDataTopic.trim(),
            personelSosTopic = personelSosTopic.trim(),
            intervalMs = interval.toIntervalMs()
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(
            title = "TRACKING SETTINGS",
            subtitle = "INTERNET OR DMR RADIO"
        )

        DeliveryModeSelector(
            mode = deliveryMode,
            onModeChange = { nextMode ->
                if (nextMode != deliveryMode) pendingMode = nextMode
            }
        )

        if (deliveryMode == DeliveryMode.DMR) {
            DmrModeCard(
                readiness = mqttState.dmrReadiness,
                dmrSlot = mqttState.dmrSlot,
                sendStatus = mqttState.dmrSendStatus,
                onOpenDmr = { viewModel?.openDmrApp() },
                onDmrSlotChange = { viewModel?.saveDmrSlot(it) }
            )

            SettingsSection(title = "DEVICE IDENTITY") {
                TacticalField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = "SERIAL NUMBER",
                    placeholder = "Optional device serial number"
                )
                TacticalField(
                    value = id,
                    onValueChange = { id = it },
                    label = "ID",
                    placeholder = "Required for DMR payload"
                )
                Button(
                    onClick = { viewModel?.saveSerialNumberAndId(serialNumber.trim(), id.trim()) },
                    enabled = id.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TacticalCardElevated,
                        contentColor = TacticalCyan
                    )
                ) {
                    Text(
                        text = "SAVE SERIAL NUMBER & ID",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                }
            }
        } else {
        Text(
            text = when (mqttState.connectionState) {
                MqttConnectionState.CONNECTED -> "● CONNECTED"
                MqttConnectionState.CONNECTING -> "● CONNECTING"
                MqttConnectionState.ERROR -> "● ERROR: ${mqttState.error.orEmpty()}"
                MqttConnectionState.NOT_CONFIGURED -> "● NOT CONFIGURED"
                MqttConnectionState.DISCONNECTED -> "● DISCONNECTED"
            },
            color = if (mqttState.connectionState == MqttConnectionState.CONNECTED) {
                com.example.dronepilottracking2026.ui.theme.TacticalGreen
            } else {
                TacticalMuted
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        SettingsSection(title = "CONNECTION") {
            TacticalField(
                value = server,
                onValueChange = { server = it },
                label = "SERVER HOST",
                placeholder = "broker.example.com"
            )

            ConnectionTypeSelector(
                useWebSocket = useWebSocket,
                onUseWebSocketChange = { useWebSocket = it }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TacticalField(
                    value = tcpPort,
                    onValueChange = { tcpPort = it },
                    label = "TCP PORT",
                    placeholder = "1883",
                    modifier = Modifier.weight(1f)
                )
                TacticalField(
                    value = wsPort,
                    onValueChange = { wsPort = it },
                    label = "WS PORT",
                    placeholder = "9001",
                    modifier = Modifier.weight(1f)
                )
            }

            TacticalField(
                value = username,
                onValueChange = { username = it },
                label = "USERNAME",
                placeholder = "MQTT username"
            )

            TacticalField(
                value = password,
                onValueChange = { password = it },
                label = "PASSWORD",
                placeholder = "MQTT password",
                isPassword = true
            )

        }

        SettingsSection(title = "MQTT TOPICS") {
            TacticalField(
                value = personelDataTopic,
                onValueChange = { personelDataTopic = it },
                label = "PERSONEL DATA",
                placeholder = ""
            )

            TacticalField(
                value = personelSosTopic,
                onValueChange = { personelSosTopic = it },
                label = "PERSONEL SOS",
                placeholder = ""
            )


            IntervalSelector(
                value = interval,
                onValueChange = {
                    interval = it
                    it.toIntervalMs()?.let { intervalMs -> viewModel?.saveInterval(intervalMs) }
                }
            )
        }

        SettingsSection(title = "DEVICE IDENTITY") {
            TacticalField(
                value = serialNumber,
                onValueChange = { serialNumber = it },
                label = "SERIAL NUMBER",
                placeholder = "Device serial number"
            )
            TacticalField(
                value = id,
                onValueChange = { id = it },
                label = "ID",
                placeholder = "Device ID"
            )
        }

        Button(
            onClick = { viewModel?.saveSerialNumberAndId(serialNumber.trim(), id.trim()) },
            enabled = serialNumber.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TacticalCardElevated,
                contentColor = TacticalCyan
            )
        ) {
            Text(
                text = "SAVE SERIAL NUMBER & ID",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
        }

        Button(
            onClick = { viewModel?.saveAndConnect(currentConfig()) },
            enabled = formReady,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TacticalCyan,
                contentColor = TacticalButtonText
            )
        ) {
            Text(
                text = "SAVE MQTT CONFIGURATION",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
        }

        Button(
            onClick = { viewModel?.testConnection(currentConfig()) },
            enabled = connectionReady,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TacticalCardElevated,
                contentColor = TacticalText
            )
        ) {
            Text(
                text = "TEST CONNECTION",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
        }

        mqttState.testResult?.let { result ->
            Text(
                text = result,
                modifier = Modifier.fillMaxWidth(),
                color = if (result == "Connection test succeeded") {
                    com.example.dronepilottracking2026.ui.theme.TacticalGreen
                } else {
                    TacticalMuted
                },
                fontSize = 10.sp
            )
        }

        if (mqttState.error != null) {
            Text(
                text = mqttState.error.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                color = com.example.dronepilottracking2026.ui.theme.TacticalRed,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        } else if (mqttState.saved) {
            Text(
                text = "CONFIGURATION SAVED  •  CONNECTING...",
                modifier = Modifier.fillMaxWidth(),
                color = com.example.dronepilottracking2026.ui.theme.TacticalGreen,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        } else {
            Text(
                text = "CONFIGURATION IS STORED LOCALLY  •  CONNECTION STARTS AFTER SAVE",
                modifier = Modifier.fillMaxWidth(),
                color = TacticalMuted,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp
            )
        }
        }
    }

    pendingMode?.let { nextMode ->
        val dialogShape = RoundedCornerShape(12.dp)
        val buttonShape = RoundedCornerShape(8.dp)

        AlertDialog(
            onDismissRequest = { pendingMode = null },
            modifier = Modifier.border(1.dp, TacticalBorder, dialogShape),
            shape = dialogShape,
            containerColor = TacticalCard,
            tonalElevation = 0.dp,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SWITCH DELIVERY MODE",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // pengganti HorizontalDivider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TacticalBorder)
                    )
                    Text(
                        text = if (nextMode == DeliveryMode.DMR) {
                            "Switch to DMR mode? MQTT will be stopped and data will be sent over radio."
                        } else {
                            "Switch to INTERNET mode? DMR delivery will be stopped and MQTT will be used."
                        },
                        color = TacticalMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel?.setDeliveryMode(nextMode)
                        pendingMode = null
                    },
                    modifier = Modifier
                        .clip(buttonShape)
                        .background(TacticalCyan) // ganti sesuai warna aksen lo
                ) {
                    Text(
                        text = "SWITCH",
                        color = TacticalCard,     // warna gelap biar teks kebaca di atas cyan
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            },
            dismissButton = {
                // pengganti OutlinedButton
                TextButton(
                    onClick = { pendingMode = null },
                    modifier = Modifier
                        .clip(buttonShape)
                        .border(1.dp, TacticalBorder, buttonShape)
                ) {
                    Text(
                        text = "CANCEL",
                        color = TacticalMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        )
    }

    if (showNotificationAccessDialog) {
        val dialogShape = RoundedCornerShape(12.dp)
        AlertDialog(
            onDismissRequest = { showNotificationAccessDialog = false },
            modifier = Modifier.border(1.dp, TacticalBorder, dialogShape),
            shape = dialogShape,
            containerColor = TacticalCard,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "NOTIFICATION ACCESS REQUIRED",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "DMR mode needs notification access to monitor Tooker status. Android Settings will open so you can allow it.",
                    color = TacticalMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationAccessDialog = false
                        viewModel?.openDmrNotificationAccessSettings()
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TacticalCyan)
                ) {
                    Text(
                        text = "OPEN SETTINGS",
                        color = TacticalCard,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationAccessDialog = false }) {
                    Text(
                        text = "LATER",
                        color = TacticalMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
private fun DeliveryModeSelector(
    mode: DeliveryMode,
    onModeChange: (DeliveryMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "SELECT TRACKING MODE",
            color = TacticalMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TacticalCard)
                .border(1.dp, TacticalBorder, RoundedCornerShape(18.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DeliveryModeOption(
                label = "INTERNET",
                selected = mode == DeliveryMode.INTERNET,
                onClick = { onModeChange(DeliveryMode.INTERNET) },
                modifier = Modifier.weight(1f)
            )
            DeliveryModeOption(
                label = "DMR",
                selected = mode == DeliveryMode.DMR,
                onClick = { onModeChange(DeliveryMode.DMR) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DeliveryModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) TacticalCyan else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "delivery-mode-background"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) TacticalButtonText else TacticalMuted,
        animationSpec = tween(durationMillis = 180),
        label = "delivery-mode-text"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(15.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DmrModeCard(
    readiness: DmrReadiness,
    dmrSlot: Int,
    sendStatus: DmrSendStatus?,
    onOpenDmr: () -> Unit,
    onDmrSlotChange: (Int) -> Unit
) {
    SettingsSection(title = "DMR RADIO HANDOFF") {
        ReadinessRow("DMR APP", if (readiness.installed) "INSTALLED" else "NOT INSTALLED", readiness.installed)
        ReadinessRow("APP STATUS", if (readiness.running) "RUNNING" else "NOT RUNNING", readiness.running)
        Text(
            text = if (readiness.isReady) {
                "DMR app is running. Local handoff is ready."
            } else {
                "Open the DMR app before starting DMR tracking."
            },
            color = TacticalMuted,
            fontSize = 11.sp
        )
        DmrSlotSelector(
            slot = dmrSlot,
            onSlotChange = onDmrSlotChange
        )
        sendStatus?.let {
            Text(
                text = if (it.success) "DMR HANDOFF OK" else "DMR HANDOFF FAILED",
                color = if (it.success) com.example.dronepilottracking2026.ui.theme.TacticalGreen else com.example.dronepilottracking2026.ui.theme.TacticalRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Button(
            onClick = onOpenDmr,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TacticalCardElevated,
                contentColor = TacticalText
            )
        ) {
            Text("BUKA DMR", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }
    }
}

private val dmrSlotOptions = (1..6).toList()

@Composable
private fun DmrSlotSelector(
    slot: Int,
    onSlotChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "DMR SLOT",
            color = TacticalMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Box {
            OutlinedTextField(
                value = "SLOT $slot  •  ${(slot - 1) * 2} SECOND OFFSET",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TacticalCardElevated,
                    unfocusedContainerColor = TacticalCardElevated,
                    focusedBorderColor = TacticalAmber,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText
                )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                dmrSlotOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("SLOT $option  •  ${(option - 1) * 2} SECOND OFFSET") },
                        onClick = {
                            onSlotChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadinessRow(label: String, value: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TacticalMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(
            value,
            color = if (ready) com.example.dronepilottracking2026.ui.theme.TacticalGreen else TacticalAmber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val intervalOptions = listOf(
    "1 second", "2 seconds", "5 seconds", "10 seconds",
    "15 seconds", "30 seconds", "1 minute", "2 minutes",
    "5 minutes", "10 minutes"
)

private fun String.toIntervalMs(): Long? {
    if (isBlank()) return null
    val number = filter { it.isDigit() }.toLongOrNull() ?: return null
    return if (contains("minute")) number * 60_000L else number * 1_000L
}

private fun Long.toIntervalLabel(): String? = when (this) {
    1_000L -> "1 second"
    2_000L -> "2 seconds"
    5_000L -> "5 seconds"
    10_000L -> "10 seconds"
    15_000L -> "15 seconds"
    30_000L -> "30 seconds"
    60_000L -> "1 minute"
    120_000L -> "2 minutes"
    300_000L -> "5 minutes"
    600_000L -> "10 minutes"
    else -> null
}

@Composable
private fun IntervalSelector(
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "SEND INTERVAL",
            color = TacticalMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                placeholder = { Text("Select interval", color = TacticalMuted.copy(alpha = 0.7f)) },
                trailingIcon = {
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = TacticalAmber,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TacticalCardElevated,
                    unfocusedContainerColor = TacticalCardElevated,
                    focusedBorderColor = TacticalAmber,
                    unfocusedBorderColor = TacticalBorder,
                    focusedTextColor = TacticalText,
                    unfocusedTextColor = TacticalText
                )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                intervalOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TacticalBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = {
            Text(
                text = title,
                color = TacticalAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            content()
        }
    )
}

@Composable
private fun ConnectionTypeSelector(
    useWebSocket: Boolean,
    onUseWebSocketChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "CONNECTION TYPE",
            color = TacticalMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = !useWebSocket,
                onClick = { onUseWebSocketChange(false) }
            )
            Text(text = "TCP", color = TacticalText, fontSize = 13.sp)
            RadioButton(
                selected = useWebSocket,
                onClick = { onUseWebSocketChange(true) }
            )
            Text(text = "WebSocket", color = TacticalText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TacticalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
            visualTransformation = if (isPassword) {
                if (passwordVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                }
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            text = if (passwordVisible) "◉" else "◌",
                            color = TacticalCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                null
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TacticalCardElevated,
                unfocusedContainerColor = TacticalCardElevated,
                focusedBorderColor = TacticalAmber,
                unfocusedBorderColor = TacticalBorder,
                focusedTextColor = TacticalText,
                unfocusedTextColor = TacticalText,
                cursorColor = TacticalAmber
            )
        )
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            color = TacticalText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = subtitle,
            color = TacticalMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TacticalBorder.copy(alpha = 0.55f))
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B110D)
@Composable
private fun MqttSettingsScreenPreview() {
    DronePilotTracking2026Theme(dynamicColor = false, darkTheme = true) {
        MqttSettingsScreen()
    }
}

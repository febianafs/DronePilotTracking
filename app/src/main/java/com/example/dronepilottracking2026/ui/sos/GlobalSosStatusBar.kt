package com.example.dronepilottracking2026.ui.sos

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dronepilottracking2026.ui.theme.TacticalCard
import com.example.dronepilottracking2026.ui.theme.TacticalCardElevated
import com.example.dronepilottracking2026.ui.theme.TacticalCyan
import com.example.dronepilottracking2026.ui.theme.TacticalMuted
import com.example.dronepilottracking2026.ui.theme.TacticalRed
import com.example.dronepilottracking2026.ui.theme.TacticalText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private const val HOLD_START_VIBRATION_MS = 100L
private val SOS_BUTTON_WIDTH = 140.dp
private val SOS_BUTTON_HEIGHT = 54.dp

/** Short silent pulse confirming the SOS hold has started. */
private fun vibrateHoldStart(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() != true) return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(HOLD_START_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(HOLD_START_VIBRATION_MS)
        }
    }
}

@Composable
fun GlobalSosStatusBar(
    sosActive: Boolean,
    onSos: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(
        label = "sos-top-bar-blink"
    )

    val blinkProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 500
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos-top-bar-alpha"
    )

    val context = LocalContext.current
    var holding by remember { mutableStateOf(false) }
    var triggered by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sosActive) {
        if (!sosActive) {
            triggered = false
            holding = false
            progress = 0f
        }
    }

    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }

        val startedAt = SystemClock.elapsedRealtime()
        while (isActive && holding && progress < 1f) {
            progress =
                (
                        (SystemClock.elapsedRealtime() - startedAt)
                            .toFloat() / 2_000L
                        ).coerceIn(0f, 1f)

            if (
                progress >= 1f &&
                !triggered
            ) {
                triggered = true
                onSos()
            }

            delay(16L)
        }
    }

    val barColor = if (sosActive) {
        androidx.compose.ui.graphics.lerp(
            Color(0xFF3D0000),
            Color(0xFFFF1515),
            blinkProgress
        )
    } else {
        TacticalCard
    }

    val textColor =
        if (sosActive) Color.White
        else TacticalText

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(barColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (sosActive) "⚠  EMERGENCY SOS ACTIVE" else "DRONE PILOT TRACKING",
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
        )

        if (sosActive) {
            Box(
                modifier = Modifier
                    .size(width = SOS_BUTTON_WIDTH, height = SOS_BUTTON_HEIGHT)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(5.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onClear() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "CLEAR SOS",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(width = SOS_BUTTON_WIDTH, height = SOS_BUTTON_HEIGHT)
                    .background(TacticalCyan.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            triggered = false
                            holding = true
                            vibrateHoldStart(context)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    // Keep the hold active through small finger drift outside the button.
                                    change.consume()
                                }
                            } finally {
                                holding = false
                                if (!triggered) progress = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (triggered) "SOS ACTIVE" else if (holding) "HOLD..." else "HOLD SOS",
                    color = TacticalCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = TacticalCyan,
                    trackColor = TacticalCardElevated
                )
            }
        }
    }
}

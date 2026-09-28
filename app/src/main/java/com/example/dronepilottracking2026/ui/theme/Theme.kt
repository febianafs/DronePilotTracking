package com.example.dronepilottracking2026.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TacticalDarkColorScheme = darkColorScheme(
    primary = TacticalCyan,
    onPrimary = TacticalButtonText,
    secondary = TacticalCyanMuted,
    onSecondary = TacticalText,
    tertiary = TacticalAmber,
    background = TacticalBackground,
    onBackground = TacticalText,
    surface = TacticalPanel,
    onSurface = TacticalText,
    surfaceVariant = TacticalCard,
    onSurfaceVariant = TacticalMuted,
    outline = TacticalBorder,
    error = TacticalRed,
    onError = TacticalText
)

@Composable
fun DronePilotTracking2026Theme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        TacticalDarkColorScheme
    } else {
        lightColorScheme(
            primary = TacticalCyanMuted,
            secondary = TacticalCyan,
            tertiary = TacticalAmber
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

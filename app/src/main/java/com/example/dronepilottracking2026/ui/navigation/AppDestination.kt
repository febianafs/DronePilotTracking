package com.example.dronepilottracking2026.ui.navigation

enum class NavigationIcon {
    HOME,
    SPECTRUM,
    SETTINGS
}

enum class AppDestination(
    val label: String,
    val icon: NavigationIcon
) {
    HOME("HOME", NavigationIcon.HOME),
    HEART_RATE("HEARTRATE", NavigationIcon.SPECTRUM),
    MQTT_SETTINGS("SETTINGS", NavigationIcon.SETTINGS)
}

fun AppDestination.isSelected(current: AppDestination): Boolean = this == current

fun AppDestination.shortLabel(): String = label

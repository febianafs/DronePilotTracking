package com.example.dronepilottracking2026.core.dmr

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes only whether Tooker currently has an active notification.
 * Notification content is intentionally not persisted or exposed.
 */
class DmrNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        _listenerConnected.value = true
        refreshFromActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == DmrTransport.TOOKER_PACKAGE) {
            _tookerNotificationDetected.value = true
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == DmrTransport.TOOKER_PACKAGE) {
            refreshFromActiveNotifications()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _listenerConnected.value = false
        _tookerNotificationDetected.value = false
    }

    private fun refreshFromActiveNotifications() {
        _tookerNotificationDetected.value = runCatching {
            activeNotifications?.any { it.packageName == DmrTransport.TOOKER_PACKAGE } == true
        }.getOrDefault(false)
    }

    companion object {
        private val _listenerConnected = MutableStateFlow(false)
        val listenerConnected = _listenerConnected.asStateFlow()

        private val _tookerNotificationDetected = MutableStateFlow(false)
        val tookerNotificationDetected = _tookerNotificationDetected.asStateFlow()
    }
}

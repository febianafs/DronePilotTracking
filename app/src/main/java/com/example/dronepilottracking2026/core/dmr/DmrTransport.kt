package com.example.dronepilottracking2026.core.dmr

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Handoff adapter for the vendor DMR application. It does not access the RF module directly. */
class DmrTransport(context: Context) {
    companion object {
        const val TOOKER_PACKAGE = "com.tooker.ptt"
        const val SEND_SMS_ACTION = "com.tooker.ptt.send.sms"
        const val CONTENT_EXTRA = "content"

        private val _lastSendStatus = MutableStateFlow<DmrSendStatus?>(null)
        val lastSendStatus = _lastSendStatus.asStateFlow()
    }

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun readiness(): DmrReadiness {
        val installed = runCatching {
            packageManager.getApplicationInfo(TOOKER_PACKAGE, 0)
        }.isSuccess

        return DmrReadiness(
            installed = installed,
            running = installed && isAppRunning()
        )
    }

    fun openApp(): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(TOOKER_PACKAGE)
            ?: return false
        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun isNotificationAccessGranted(): Boolean = runCatching {
        val component = ComponentName(appContext, DmrNotificationListener::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager?.isNotificationListenerAccessGranted(component) == true
        } else {
            val enabledListeners = Settings.Secure.getString(
                appContext.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            enabledListeners.split(":").any { value ->
                runCatching { ComponentName.unflattenFromString(value) == component }
                    .getOrDefault(false)
            }
        }
    }.getOrDefault(false)

    fun openNotificationAccessSettings(): Boolean = runCatching {
        appContext.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)

    fun send(payload: String): Result<Unit> {
        val state = readiness()
        if (!state.isReady) {
            val result = Result.failure<Unit>(IllegalStateException(state.failureMessage()))
            _lastSendStatus.value = DmrSendStatus(success = false, timestamp = System.currentTimeMillis())
            return result
        }

        val result = runCatching {
            val intent = Intent(SEND_SMS_ACTION).apply {
                putExtra(CONTENT_EXTRA, payload)
            }
            appContext.sendBroadcast(intent)
        }
        _lastSendStatus.value = DmrSendStatus(
            success = result.isSuccess,
            timestamp = System.currentTimeMillis()
        )
        return result
    }

    /**
     * Prefer the Tooker notification signal. Android limits visibility of
     * other app processes on modern releases, so process lookup is fallback only.
     */
    private fun isAppRunning(): Boolean {
        if (DmrNotificationListener.tookerNotificationDetected.value) {
            return true
        }

        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE)
            as? ActivityManager
            ?: return false
        val runningProcesses = activityManager.runningAppProcesses ?: return false

        return runningProcesses.any { processInfo ->
            processInfo.pkgList?.any { packageName ->
                packageName == TOOKER_PACKAGE
            } == true
        }
    }
}

data class DmrSendStatus(
    val success: Boolean,
    val timestamp: Long
)

data class DmrReadiness(
    val installed: Boolean = false,
    val running: Boolean = false
) {
    /** Ready means Tooker is installed and its process is currently visible to Android. */
    val isReady: Boolean
        get() = installed && running

    fun failureMessage(): String = when {
        !installed -> "DMR app com.tooker.ptt is not installed"
        !running -> "DMR app is not running"
        else -> "DMR handoff is not ready"
    }
}

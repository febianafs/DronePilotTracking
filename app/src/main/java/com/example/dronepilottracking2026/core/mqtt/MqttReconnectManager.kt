package com.example.dronepilottracking2026.core.mqtt

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MqttReconnectManager(
    context: Context,
    private val mqttManager: MqttManager
) {
    companion object {
        private const val TAG = "MQTT_RECONNECT"
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val RECONNECT_GUARD_MS = 10_000L
    }

    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var activeNetwork: Network? = null
    private var reconnectGuardJob: Job? = null
    @Volatile private var isReconnecting = false
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        registerNetworkCallback()
        startWatchdog()
    }

    fun stop() {
        started = false
        watchdogJob?.cancel()
        watchdogJob = null
        reconnectGuardJob?.cancel()
        reconnectGuardJob = null
        unregisterNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val validated = capabilities?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) == true

                if (!validated) {
                    Log.d(TAG, "Network available but not validated; skip reconnect")
                    return
                }

                val changed = activeNetwork != null && activeNetwork != network
                activeNetwork = network
                if (changed || !mqttManager.isConnected()) {
                    Log.d(TAG, "Validated default network available; MQTT reconnect needed")
                    reconnectNow(if (changed) "default network changed" else "network available", force = changed)
                } else {
                    Log.d(TAG, "Validated network available; MQTT already connected")
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                if (activeNetwork == network) activeNetwork = null
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to register network callback", error)
        }
    }

    private fun unregisterNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        try {
            networkCallback?.let(connectivityManager::unregisterNetworkCallback)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to unregister network callback", error)
        } finally {
            networkCallback = null
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!mqttManager.isConnected()) {
                    reconnectNow("watchdog")
                }
            }
        }
    }

    private fun reconnectNow(reason: String, force: Boolean = false) {
        if (isReconnecting || !started) return
        isReconnecting = true
        Log.d(TAG, "Reconnect triggered: $reason")

        scope.launch {
            try {
                mqttManager.reconnect(force)
            } catch (error: Exception) {
                Log.e(TAG, "Reconnect failed: ${error.message}", error)
            } finally {
                reconnectGuardJob?.cancel()
                reconnectGuardJob = launch {
                    delay(RECONNECT_GUARD_MS)
                    isReconnecting = false
                }
            }
        }
    }
}

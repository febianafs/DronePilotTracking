package com.example.dronepilottracking2026.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.example.dronepilottracking2026.core.security.AppCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.mqttConfigDataStore by preferencesDataStore(name = "mqtt_config")

class MqttConfigDataStore(private val context: Context) {
    private object Keys {
        val host = stringPreferencesKey("host")
        val tcpPort = intPreferencesKey("tcp_port")
        val wsPort = intPreferencesKey("ws_port")
        val username = stringPreferencesKey("username")
        val password = stringPreferencesKey("password")
        val serialNumber = stringPreferencesKey("serial_number")
        val id = stringPreferencesKey("id")

        val useWebSocket = booleanPreferencesKey("use_websocket")
        val useTls = booleanPreferencesKey("use_tls")
        val personelDataTopic = stringPreferencesKey("personel_data_topic")
        val personelSosTopic = stringPreferencesKey("personel_sos_topic")
        val intervalMs = longPreferencesKey("interval_ms")
    }

    val config: Flow<MqttConfig> = context.mqttConfigDataStore.data.map { preferences ->
        val useWebSocket = preferences[Keys.useWebSocket] ?: false
        val tcpPort = preferences[Keys.tcpPort]
        val wsPort = preferences[Keys.wsPort]
        // Existing configurations without this option continue using the previous plaintext transport.
        val useTls = preferences[Keys.useTls] ?: false
        MqttConfig(
            host = preferences[Keys.host].orEmpty(),
            tcpPort = tcpPort,
            wsPort = wsPort,
            username = preferences[Keys.username].orEmpty(),
            password = runCatching { AppCrypto.decryptOrLegacy(preferences[Keys.password].orEmpty()) }.getOrDefault(""),
            serialNumber = preferences[Keys.serialNumber].orEmpty(),
            id = preferences[Keys.id].orEmpty(),

            useWebSocket = useWebSocket,
            useTls = useTls,
            personelDataTopic = preferences[Keys.personelDataTopic].orEmpty(),
            personelSosTopic = preferences[Keys.personelSosTopic].orEmpty(),
            intervalMs = preferences[Keys.intervalMs] ?: 5_000L
        )
    }.onEach { config ->
        // Migrate passwords saved by earlier app versions to Keystore encryption.
        if (config.password.isNotBlank()) {
            context.mqttConfigDataStore.edit { preferences ->
                val stored = preferences[Keys.password].orEmpty()
                if (stored.isNotBlank() && !AppCrypto.isEncrypted(stored)) {
                    preferences[Keys.password] = AppCrypto.encrypt(stored)
                }
            }
        }
    }

    suspend fun save(config: MqttConfig) {
        context.mqttConfigDataStore.edit { preferences ->
            preferences[Keys.host] = config.host
            if (config.tcpPort == null) preferences.remove(Keys.tcpPort) else preferences[Keys.tcpPort] = config.tcpPort
            if (config.wsPort == null) preferences.remove(Keys.wsPort) else preferences[Keys.wsPort] = config.wsPort
            preferences[Keys.username] = config.username
            preferences[Keys.password] = AppCrypto.encrypt(config.password)
            preferences[Keys.serialNumber] = config.serialNumber
            preferences[Keys.id] = config.id

            preferences[Keys.useWebSocket] = config.useWebSocket
            preferences[Keys.useTls] = config.useTls
            preferences[Keys.personelDataTopic] = config.personelDataTopic
            preferences[Keys.personelSosTopic] = config.personelSosTopic
            if (config.intervalMs == null) preferences.remove(Keys.intervalMs) else preferences[Keys.intervalMs] = config.intervalMs
        }
    }
}

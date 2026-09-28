package com.example.dronepilottracking2026.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dronepilottracking2026.data.model.MqttConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mqttConfigDataStore by preferencesDataStore(name = "mqtt_config")

class MqttConfigDataStore(private val context: Context) {
    private object Keys {
        val host = stringPreferencesKey("host")
        val tcpPort = intPreferencesKey("tcp_port")
        val wsPort = intPreferencesKey("ws_port")
        val username = stringPreferencesKey("username")
        val password = stringPreferencesKey("password")
        val useWebSocket = booleanPreferencesKey("use_websocket")
        val personelDataTopic = stringPreferencesKey("personel_data_topic")
        val personelSosTopic = stringPreferencesKey("personel_sos_topic")
        val intervalMs = longPreferencesKey("interval_ms")
    }

    val config: Flow<MqttConfig> = context.mqttConfigDataStore.data.map { preferences ->
        MqttConfig(
            host = preferences[Keys.host].orEmpty(),
            tcpPort = preferences[Keys.tcpPort],
            wsPort = preferences[Keys.wsPort],
            username = preferences[Keys.username].orEmpty(),
            password = preferences[Keys.password].orEmpty(),
            useWebSocket = preferences[Keys.useWebSocket] ?: false,
            personelDataTopic = preferences[Keys.personelDataTopic].orEmpty(),
            personelSosTopic = preferences[Keys.personelSosTopic].orEmpty(),
            intervalMs = preferences[Keys.intervalMs] ?: 5_000L
        )
    }

    suspend fun save(config: MqttConfig) {
        context.mqttConfigDataStore.edit { preferences ->
            preferences[Keys.host] = config.host
            config.tcpPort?.let { preferences[Keys.tcpPort] = it }
            config.wsPort?.let { preferences[Keys.wsPort] = it }
            preferences[Keys.username] = config.username
            preferences[Keys.password] = config.password
            preferences[Keys.useWebSocket] = config.useWebSocket
            preferences[Keys.personelDataTopic] = config.personelDataTopic
            preferences[Keys.personelSosTopic] = config.personelSosTopic
            config.intervalMs?.let { preferences[Keys.intervalMs] = it }
        }
    }
}

package com.example.dronepilottracking2026.core.mqtt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.mqttQueueDataStore by preferencesDataStore(name = "mqtt_queue")

data class MqttQueueEntity(
    val id: Long,
    val topic: String,
    val payload: String,
    val qos: Int,
    val kind: String = "NORMAL"
)

class MqttQueueManager(private val context: Context) {
    private val key = stringPreferencesKey("messages")

    suspend fun save(topic: String, payload: String, qos: Int, kind: String = "NORMAL") {
        context.mqttQueueDataStore.edit { preferences ->
            val messages = parseQueue(preferences[key].orEmpty())
            messages.put(JSONObject().apply {
                put("id", System.currentTimeMillis())
                put("topic", topic)
                put("payload", payload)
                put("qos", qos)
                put("kind", kind)
            })
            preferences[key] = messages.toString()
        }
    }

    suspend fun flush(publish: suspend (MqttQueueEntity) -> Boolean) {
        val current = readMessages().toMutableList()
        val remaining = mutableListOf<MqttQueueEntity>()
        for (message in current) {
            if (!publish(message)) {
                remaining.add(message)
                remaining.addAll(current.drop(current.indexOf(message) + 1))
                break
            }
        }
        context.mqttQueueDataStore.edit { preferences ->
            preferences[key] = JSONArray().apply {
                remaining.forEach { message ->
                    put(JSONObject().apply {
                        put("id", message.id)
                        put("topic", message.topic)
                        put("payload", message.payload)
                        put("qos", message.qos)
                        put("kind", message.kind)
                    })
                }
            }.toString()
        }
    }

    private suspend fun readMessages(): List<MqttQueueEntity> {
        val raw = context.mqttQueueDataStore.data.first()[key].orEmpty()
        val json = parseQueue(raw)
        return (0 until json.length()).mapNotNull { index ->
            runCatching {
                val item = json.getJSONObject(index)
                MqttQueueEntity(
                    id = item.getLong("id"),
                    topic = item.getString("topic"),
                    payload = item.getString("payload"),
                    qos = item.getInt("qos"),
                    kind = item.optString("kind", "NORMAL")
                )
            }.getOrNull()
        }
    }

    private fun parseQueue(raw: String): JSONArray {
        if (raw.isBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
}

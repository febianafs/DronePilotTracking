package com.example.dronepilottracking2026.core.mqtt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dronepilottracking2026.core.security.AppCrypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mutex = Mutex()

    suspend fun save(topic: String, payload: String, qos: Int, kind: String = "NORMAL") {
        mutex.withLock { context.mqttQueueDataStore.edit { preferences ->
            val messages = parseQueue(runCatching { AppCrypto.decryptOrLegacy(preferences[key].orEmpty()) }.getOrDefault(""))
            val existing = readEntities(messages)
            // Tracking is state: retain only the newest location. SOS is also state,
            // so an offline clear supersedes an older pending activation (and vice versa).
            val retained = when (kind) {
                "NORMAL", "LOCATION" -> existing.filterNot { it.topic == topic && it.kind in setOf("NORMAL", "LOCATION") }
                "SOS", "SOS_CLEAR" -> existing.filterNot { it.topic == topic && it.kind in setOf("SOS", "SOS_CLEAR") }
                else -> existing
            }
            val updated = (retained + MqttQueueEntity(System.currentTimeMillis(), topic, payload, qos, kind))
                .takeLast(MAX_QUEUE_SIZE)
                .sortedBy { if (it.kind == "SOS" || it.kind == "SOS_CLEAR") 0 else 1 }
            preferences[key] = AppCrypto.encrypt(toJson(updated).toString())
        } }
    }

    suspend fun flush(publish: suspend (MqttQueueEntity) -> Boolean) {
        mutex.withLock {
        val current = readMessages().toMutableList()
        val remaining = mutableListOf<MqttQueueEntity>()
        for (message in current) {
            if (!publish(message)) {
                remaining.add(message)
                remaining.addAll(current.drop(current.indexOf(message) + 1))
                break
            }
        }
        context.mqttQueueDataStore.edit { preferences -> preferences[key] = AppCrypto.encrypt(toJson(remaining).toString()) }
        }
    }

    private suspend fun readMessages(): List<MqttQueueEntity> {
        val raw = runCatching { AppCrypto.decryptOrLegacy(context.mqttQueueDataStore.data.first()[key].orEmpty()) }
            .getOrDefault("")
        val json = parseQueue(raw)
        return readEntities(json)
    }

    private fun readEntities(json: JSONArray): List<MqttQueueEntity> =
        (0 until json.length()).mapNotNull { index ->
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

    private fun toJson(messages: List<MqttQueueEntity>) = JSONArray().apply {
        messages.forEach { message -> put(JSONObject().apply {
            put("id", message.id); put("topic", message.topic); put("payload", message.payload)
            put("qos", message.qos); put("kind", message.kind)
        }) }
    }

    private companion object { const val MAX_QUEUE_SIZE = 500 }

    private fun parseQueue(raw: String): JSONArray {
        if (raw.isBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
}

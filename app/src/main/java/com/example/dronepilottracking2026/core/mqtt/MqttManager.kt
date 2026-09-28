package com.example.dronepilottracking2026.core.mqtt

import android.content.Context
import android.os.Build
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class MqttManager(context: Context) {
    companion object {
        const val QOS_DATA = 1
        const val QOS_SOS = 2
        private const val INITIAL_RETRY_DELAY_MS = 5_000L
        private const val MAX_RETRY_DELAY_MS = 300_000L
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)
    private val queue = MqttQueueManager(appContext)
    private val generation = AtomicInteger(0)
    private var client: Mqtt3AsyncClient? = null
    private var retryJob: Job? = null
    private var retryCount = 0
    private var intentionallyStopped = false
    private var activeConfig: MqttConfig? = null

    var onStateChanged: ((MqttManagerState) -> Unit)? = null
    var onPublishFailed: ((String, String) -> Unit)? = null
    var onSosMessageReceived: ((topic: String, payload: String) -> Unit)? = null
    private var activeSosTopic: String? = null

    fun connect(config: MqttConfig) {
        if (!config.isComplete) {
            onStateChanged?.invoke(MqttManagerState.ERROR("MQTT configuration is incomplete"))
            return
        }
        activeConfig = config
        intentionallyStopped = false
        retryCount = 0
        val currentGeneration = generation.incrementAndGet()
        onStateChanged?.invoke(MqttManagerState.CONNECTING)
        scope.launch { doConnect(config, currentGeneration) }
    }

    fun disconnect() {
        intentionallyStopped = true
        retryJob?.cancel()
        generation.incrementAndGet()
        val oldClient = client
        client = null
        activeSosTopic = null
        scope.launch { runCatching { oldClient?.disconnect() } }
        onStateChanged?.invoke(MqttManagerState.DISCONNECTED)
    }

    fun reconnect() {
        val config = activeConfig ?: return
        if (isConnected()) return
        connect(config)
    }

    fun testConnection(
        config: MqttConfig,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (!config.isConnectionComplete) {
            onResult(Result.failure(IllegalArgumentException("MQTT connection fields are incomplete")))
            return
        }
        val port = if (config.useWebSocket) config.wsPort else config.tcpPort
        if (port == null) {
            onResult(Result.failure(IllegalArgumentException("MQTT port is invalid")))
            return
        }
        scope.launch {
            try {
                val clientId = "android_test_${System.currentTimeMillis()}".take(23)
                val builder = Mqtt3Client.builder()
                    .identifier(clientId)
                    .serverHost(config.host)
                    .serverPort(port)
                if (config.useWebSocket) {
                    builder.webSocketConfig().serverPath("/").applyWebSocketConfig()
                }
                builder.simpleAuth()
                    .username(config.username)
                    .password(config.password.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                val testClient = builder.buildAsync()
                val ack = testClient.connect().get()
                if (ack.returnCode == Mqtt3ConnAckReturnCode.SUCCESS) {
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(IllegalStateException("Broker rejected: ${ack.returnCode}")))
                }
                runCatching { testClient.disconnect().get() }
            } catch (error: Exception) {
                onResult(Result.failure(error))
            }
        }
    }

    fun isConnected(): Boolean = client?.state?.isConnected == true

    suspend fun publish(topic: String, payload: String, qos: Int = QOS_DATA): Boolean {
        if (!isConnected()) {
            queue.save(topic, payload, qos)
            onPublishFailed?.invoke(topic, "Queued offline")
            return false
        }
        return publishNow(
            MqttQueueEntity(
                id = System.currentTimeMillis(),
                topic = topic,
                payload = payload,
                qos = qos
            )
        )
    }

    private suspend fun doConnect(config: MqttConfig, currentGeneration: Int) {
        if (generation.get() != currentGeneration) return
        val port = if (config.useWebSocket) config.wsPort else config.tcpPort
        if (port == null) return
        val identifier = "android_${Build.MODEL}_${System.currentTimeMillis()}"
            .replace(" ", "_")
            .take(23)
        val builder = Mqtt3Client.builder()
            .identifier(identifier)
            .serverHost(config.host)
            .serverPort(port)

        if (config.useWebSocket) {
            builder.webSocketConfig().serverPath("/").applyWebSocketConfig()
        }
        builder.simpleAuth()
            .username(config.username)
            .password(config.password.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()

        val newClient = builder.buildAsync()
        client = newClient
        newClient.connect().whenComplete { ack: Mqtt3ConnAck?, error: Throwable? ->
            if (generation.get() != currentGeneration) return@whenComplete
            if (error != null || ack?.returnCode != Mqtt3ConnAckReturnCode.SUCCESS) {
                onStateChanged?.invoke(MqttManagerState.ERROR(error?.message ?: "Broker rejected connection"))
                if (!intentionallyStopped) scheduleRetry(config, currentGeneration)
            } else {
                retryCount = 0
                subscribeSosTopic(newClient, config.personelSosTopic)
                onStateChanged?.invoke(MqttManagerState.CONNECTED)
                scope.launch { queue.flush { publishNow(it) } }
            }
        }
    }

    private fun subscribeSosTopic(client: Mqtt3AsyncClient, topic: String) {
        val normalizedTopic = topic.trim()
        if (normalizedTopic.isEmpty() || activeSosTopic == normalizedTopic) return
        client.subscribeWith()
            .topicFilter(normalizedTopic)
            .qos(MqttQos.EXACTLY_ONCE)
            .callback { publish: Mqtt3Publish ->
                if (publish.isRetain) return@callback
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                onSosMessageReceived?.invoke(normalizedTopic, payload)
            }
            .send()
            .whenComplete { _, error ->
                if (error == null) {
                    activeSosTopic = normalizedTopic
                } else {
                    onPublishFailed?.invoke(
                        normalizedTopic,
                        "SOS subscription failed: ${error.message}"
                    )
                }
            }
    }

    private suspend fun publishNow(message: MqttQueueEntity): Boolean {
        return try {
            client?.publishWith()
                ?.topic(message.topic)
                ?.qos(if (message.qos >= 2) MqttQos.EXACTLY_ONCE else MqttQos.AT_LEAST_ONCE)
                ?.payload(message.payload.toByteArray(StandardCharsets.UTF_8))
                ?.retain(false)
                ?.send()
                ?.get()
            true
        } catch (error: Exception) {
            onPublishFailed?.invoke(message.topic, error.message ?: "Publish failed")
            false
        }
    }

    private fun scheduleRetry(config: MqttConfig, currentGeneration: Int) {
        retryJob?.cancel()
        retryJob = scope.launch {
            retryCount++
            val multiplier = 1L shl (retryCount - 1).coerceAtMost(6)
            val delayMs = minOf(INITIAL_RETRY_DELAY_MS * multiplier, MAX_RETRY_DELAY_MS)
            delay(delayMs)
            if (!intentionallyStopped && generation.get() == currentGeneration) {
                doConnect(config, currentGeneration)
            }
        }
    }
}

sealed interface MqttManagerState {
    data object CONNECTING : MqttManagerState
    data object CONNECTED : MqttManagerState
    data object DISCONNECTED : MqttManagerState
    data class ERROR(val message: String) : MqttManagerState
}

package com.example.dronepilottracking2026.core.mqtt

import android.content.Context
import android.os.Build
import android.util.Log
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class MqttManager(context: Context) {
    companion object {
        private const val TAG = "DronePilotMQTT"
        const val QOS_DATA = 1
        const val QOS_SOS = 2
        private const val INITIAL_RETRY_DELAY_MS = 5_000L
        private const val MAX_RETRY_DELAY_MS = 300_000L
        private const val TEST_TIMEOUT_MS = 15_000L
        private const val CLOSE_TIMEOUT_MS = 5_000L
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = MqttQueueManager(appContext)
    private val generation = AtomicInteger(0)
    private val lifecycleMutex = Mutex()
    private val testMutex = Mutex()
    private var client: Mqtt3AsyncClient? = null
    private var retryJob: Job? = null
    private var retryCount = 0
    private var intentionallyStopped = false
    private var autoReconnectEnabled = true
    private var activeConfig: MqttConfig? = null
    @Volatile private var connectingConfig: MqttConfig? = null

    var onStateChanged: ((MqttManagerState) -> Unit)? = null
    var onPublishFailed: ((String, String) -> Unit)? = null
    var onPublishSucceeded: ((String, String) -> Unit)? = null
    var onSosMessageReceived: ((topic: String, payload: String) -> Unit)? = null
    private var activeSosTopic: String? = null

    @Synchronized
    fun connect(config: MqttConfig) {
        autoReconnectEnabled = true
        if (!config.isComplete) {
            Log.e(TAG, "Connect rejected: MQTT configuration is incomplete")
            onStateChanged?.invoke(MqttManagerState.ERROR("MQTT configuration is incomplete"))
            return
        }
        if (activeConfig == config && (isConnected() || connectingConfig == config)) {
            Log.d(TAG, "Connect skipped: same configuration is already connected or connecting")
            return
        }
        activeConfig = config
        connectingConfig = config
        intentionallyStopped = false
        retryCount = 0
        val currentGeneration = generation.incrementAndGet()
        onStateChanged?.invoke(MqttManagerState.CONNECTING)
        scope.launch { doConnect(config, currentGeneration) }
    }

    fun disconnect() {
        intentionallyStopped = true
        connectingConfig = null
        retryJob?.cancel()
        generation.incrementAndGet()
        val oldClient = client
        client = null
        activeSosTopic = null
        scope.launch { oldClient?.let { closeClient(it) } }
        onStateChanged?.invoke(MqttManagerState.DISCONNECTED)
    }

    fun disableAutoReconnect() {
        autoReconnectEnabled = false
        disconnect()
    }

    fun reconnect() {
        if (!autoReconnectEnabled) return
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
            testMutex.withLock {
                var testClient: Mqtt3AsyncClient? = null
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
                testClient = builder.buildAsync()
                val connectResult = withTimeoutOrNull(TEST_TIMEOUT_MS) {
                    awaitConnect(testClient!!)
                } ?: Result.failure(IllegalStateException("MQTT test connection timed out"))
                closeClient(testClient!!)
                onResult(connectResult)
                } catch (error: Throwable) {
                    testClient?.let { closeClient(it) }
                    onResult(Result.failure(error))
                }
            }
        }
    }

    fun isConnected(): Boolean = client?.state?.isConnected == true

    suspend fun publish(topic: String, payload: String, qos: Int = QOS_DATA, kind: String = "NORMAL"): Boolean {
        if (!isConnected()) {
            queue.save(topic, payload, qos, kind)
            Log.w(TAG, "Queued offline publish for topic=$topic")
            onPublishFailed?.invoke(topic, "Queued offline")
            return false
        }
        val sent = publishNow(
            MqttQueueEntity(
                id = System.currentTimeMillis(),
                topic = topic,
                payload = payload,
                qos = qos,
                kind = kind
            )
        )
        if (!sent && !isConnected()) {
            queue.save(topic, payload, qos, kind)
            Log.w(TAG, "Connection dropped during publish; queued topic=$topic")
            onPublishFailed?.invoke(topic, "Queued offline")
        }
        if (sent) onPublishSucceeded?.invoke(topic, kind)
        return sent
    }

    private suspend fun doConnect(config: MqttConfig, currentGeneration: Int) {
        if (generation.get() != currentGeneration) return
        try {
            doConnectSafely(config, currentGeneration)
        } catch (error: Exception) {
            if (generation.get() == currentGeneration && !intentionallyStopped) {
                connectingConfig = null
                Log.e(TAG, "Unable to start MQTT connection", error)
                onStateChanged?.invoke(
                    MqttManagerState.ERROR(error.message ?: "Unable to create MQTT connection")
                )
                scheduleRetry(config, currentGeneration)
            }
        }
    }

    private suspend fun doConnectSafely(config: MqttConfig, currentGeneration: Int) {
        lifecycleMutex.withLock {
            val oldClient = client
            if (oldClient != null) {
                client = null
                activeSosTopic = null
                closeClient(oldClient)
            }
        }
        if (generation.get() != currentGeneration) return
        val port = if (config.useWebSocket) config.wsPort else config.tcpPort
        if (port == null || port !in 1..65535) {
            connectingConfig = null
            onStateChanged?.invoke(MqttManagerState.ERROR("MQTT port is invalid"))
            return
        }
        Log.i(TAG, "Connecting to MQTT host=${config.host}, port=$port, websocket=${config.useWebSocket}")
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
            if (generation.get() != currentGeneration) {
                scope.launch { closeClient(newClient) }
                return@whenComplete
            }
            if (error != null || ack?.returnCode != Mqtt3ConnAckReturnCode.SUCCESS) {
                connectingConfig = null
                Log.e(TAG, "MQTT connection failed: ${error?.message ?: ack?.returnCode}")
                onStateChanged?.invoke(MqttManagerState.ERROR(error?.message ?: "Broker rejected connection"))
                scope.launch {
                    closeClient(newClient)
                    if (!intentionallyStopped && autoReconnectEnabled && generation.get() == currentGeneration) {
                        scheduleRetry(config, currentGeneration)
                    }
                }
            } else {
                connectingConfig = null
                retryCount = 0
                Log.i(TAG, "MQTT connected; subscribing to SOS topic=${config.personelSosTopic}")
                subscribeSosTopic(newClient, config.personelSosTopic)
                onStateChanged?.invoke(MqttManagerState.CONNECTED)
                scope.launch {
                    queue.flush { message ->
                        val sent = publishNow(message)
                        if (sent) onPublishSucceeded?.invoke(message.topic, message.kind)
                        sent
                    }
                }
            }
        }
    }

    private suspend fun awaitConnect(client: Mqtt3AsyncClient): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            client.connect().whenComplete { ack, error ->
                if (!continuation.isActive) return@whenComplete
                val result = when {
                    error != null -> Result.failure(error)
                    ack?.returnCode == Mqtt3ConnAckReturnCode.SUCCESS -> Result.success(Unit)
                    else -> Result.failure(
                        IllegalStateException("Broker rejected: ${ack?.returnCode}")
                    )
                }
                continuation.resume(result)
            }
        }

    private suspend fun closeClient(client: Mqtt3AsyncClient) {
        withTimeoutOrNull(CLOSE_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { continuation ->
                runCatching { client.disconnect() }
                    .onSuccess { future ->
                        future.whenComplete { _, _ ->
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    .onFailure {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
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
            val currentClient = client
            if (currentClient == null || !currentClient.state.isConnected) {
                return false
            }
            currentClient.publishWith()
                ?.topic(message.topic)
                ?.qos(if (message.qos >= 2) MqttQos.EXACTLY_ONCE else MqttQos.AT_LEAST_ONCE)
                ?.payload(message.payload.toByteArray(StandardCharsets.UTF_8))
                ?.retain(false)
                ?.send()
                ?.get()
            Log.d(TAG, "Published MQTT message to topic=${message.topic}")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Publish failed for topic=${message.topic}: ${error.message}", error)
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
                connectingConfig = config
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

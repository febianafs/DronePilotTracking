package com.example.dronepilottracking2026.data.repository

import com.example.dronepilottracking2026.data.local.MqttConfigDataStore
import com.example.dronepilottracking2026.data.model.MqttConfig
import kotlinx.coroutines.flow.Flow

class MqttConfigRepository(private val dataStore: MqttConfigDataStore) {
    val config: Flow<MqttConfig> = dataStore.config
    suspend fun save(config: MqttConfig) = dataStore.save(config)
}


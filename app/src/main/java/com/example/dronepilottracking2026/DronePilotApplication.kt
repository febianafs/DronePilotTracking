package com.example.dronepilottracking2026

import android.app.Application
import com.example.dronepilottracking2026.core.mqtt.MqttManager
import com.example.dronepilottracking2026.core.mqtt.MqttReconnectManager

class DronePilotApplication : Application() {
    lateinit var mqttManager: MqttManager
        private set
    lateinit var mqttReconnectManager: MqttReconnectManager
        private set

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager(this)
        mqttReconnectManager = MqttReconnectManager(this, mqttManager)
        mqttReconnectManager.start()
    }
}


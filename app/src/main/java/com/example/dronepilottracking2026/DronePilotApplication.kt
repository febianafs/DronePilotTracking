package com.example.dronepilottracking2026

import android.app.Application
import com.example.dronepilottracking2026.core.mqtt.MqttManager
import com.example.dronepilottracking2026.core.mqtt.MqttReconnectManager
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory

class DronePilotApplication : Application() {
    lateinit var mqttManager: MqttManager
        private set
    lateinit var mqttReconnectManager: MqttReconnectManager
        private set

    override fun onCreate() {
        super.onCreate()
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        mqttManager = MqttManager(this)
        mqttReconnectManager = MqttReconnectManager(this, mqttManager)
        mqttReconnectManager.start()
    }
}


package com.example.dronepilottracking2026.data.repository

import android.content.Context
import com.example.dronepilottracking2026.core.location.AppLocationManager
import com.example.dronepilottracking2026.data.model.LocationData
import kotlinx.coroutines.flow.Flow

class LocationRepository(context: Context) {
    private val manager = AppLocationManager(context.applicationContext)

    fun locationFlow(intervalMs: Long = 5_000L): Flow<Result<LocationData>> {
        manager.setInterval(intervalMs)
        return manager.locationFlow()
    }
}

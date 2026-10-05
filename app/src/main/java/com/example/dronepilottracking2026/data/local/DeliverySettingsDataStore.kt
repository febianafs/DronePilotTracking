package com.example.dronepilottracking2026.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dronepilottracking2026.data.model.DEFAULT_DMR_INTERVAL_MS
import com.example.dronepilottracking2026.data.model.DEFAULT_DMR_SLOT
import com.example.dronepilottracking2026.data.model.DMR_SLOT_COUNT
import com.example.dronepilottracking2026.data.model.DeliveryMode
import com.example.dronepilottracking2026.data.model.toDeliveryMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deliverySettingsDataStore by preferencesDataStore(name = "delivery_settings")

class DeliverySettingsDataStore(private val context: Context) {
    private object Keys {
        val deliveryMode = stringPreferencesKey("delivery_mode")
        val dmrIntervalMs = longPreferencesKey("dmr_interval_ms")
        val dmrSlot = longPreferencesKey("dmr_slot")
    }

    val mode: Flow<DeliveryMode> = context.deliverySettingsDataStore.data.map { preferences ->
        preferences[Keys.deliveryMode].orEmpty().toDeliveryMode()
    }

    val dmrIntervalMs: Flow<Long> = context.deliverySettingsDataStore.data.map { preferences ->
        (preferences[Keys.dmrIntervalMs] ?: DEFAULT_DMR_INTERVAL_MS)
            .coerceAtLeast(DEFAULT_DMR_INTERVAL_MS)
    }

    val dmrSlot: Flow<Int> = context.deliverySettingsDataStore.data.map { preferences ->
        (preferences[Keys.dmrSlot]?.toInt() ?: DEFAULT_DMR_SLOT)
            .coerceIn(1, DMR_SLOT_COUNT)
    }

    suspend fun setMode(mode: DeliveryMode) {
        context.deliverySettingsDataStore.edit { preferences ->
            preferences[Keys.deliveryMode] = mode.name
        }
    }

    suspend fun setDmrInterval(intervalMs: Long) {
        context.deliverySettingsDataStore.edit { preferences ->
            preferences[Keys.dmrIntervalMs] = intervalMs.coerceAtLeast(DEFAULT_DMR_INTERVAL_MS)
        }
    }

    suspend fun setDmrSlot(slot: Int) {
        context.deliverySettingsDataStore.edit { preferences ->
            preferences[Keys.dmrSlot] = slot.coerceIn(1, DMR_SLOT_COUNT).toLong()
        }
    }
}

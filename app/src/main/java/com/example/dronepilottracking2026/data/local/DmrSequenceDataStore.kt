package com.example.dronepilottracking2026.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dronepilottracking2026.data.model.DMR_SEQUENCE_MAX
import com.example.dronepilottracking2026.data.model.nextDmrSequenceValue

private val Context.dmrSequenceDataStore by preferencesDataStore(name = "dmr_sequence")

class DmrSequenceDataStore(private val context: Context) {
    private object Keys {
        val nextSequence = longPreferencesKey("next_sequence")
    }

    suspend fun nextSequence(): Long {
        var allocated = 1L
        context.dmrSequenceDataStore.edit { preferences ->
            val current = (preferences[Keys.nextSequence] ?: 1L)
                .coerceIn(0L, DMR_SEQUENCE_MAX)
            allocated = current
            preferences[Keys.nextSequence] = nextDmrSequenceValue(current)
        }
        return allocated
    }
}

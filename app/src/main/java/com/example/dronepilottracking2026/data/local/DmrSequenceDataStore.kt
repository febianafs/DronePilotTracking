package com.example.dronepilottracking2026.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.dmrSequenceDataStore by preferencesDataStore(name = "dmr_sequence")

class DmrSequenceDataStore(private val context: Context) {
    private object Keys {
        val nextSequence = longPreferencesKey("next_sequence")
    }

    suspend fun nextSequence(): Long {
        var allocated = 1L
        context.dmrSequenceDataStore.edit { preferences ->
            val current = (preferences[Keys.nextSequence] ?: 1L).coerceAtLeast(1L)
            allocated = current
            preferences[Keys.nextSequence] = if (current == Long.MAX_VALUE) 1L else current + 1L
        }
        return allocated
    }
}

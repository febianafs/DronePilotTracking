package com.example.dronepilottracking2026.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "personnel_profile")

class ProfileDataStore(private val context: Context) {
    private object Keys {
        val profileCompleted = booleanPreferencesKey("profile_completed")
        val name = stringPreferencesKey("name")
        val nrp = stringPreferencesKey("nrp")
        val avatarUri = stringPreferencesKey("avatar_uri")
    }

    val profile: Flow<PersonnelProfile?> = context.profileDataStore.data.map { preferences ->
        if (preferences[Keys.profileCompleted] != true) {
            null
        } else {
            PersonnelProfile(
                name = preferences[Keys.name].orEmpty(),
                nrp = preferences[Keys.nrp].orEmpty(),
                avatarUri = preferences[Keys.avatarUri]
            )
        }
    }

    suspend fun save(profile: PersonnelProfile) {
        context.profileDataStore.edit { preferences ->
            preferences[Keys.profileCompleted] = profile.isComplete
            preferences[Keys.name] = profile.name
            preferences[Keys.nrp] = profile.nrp
            if (profile.avatarUri == null) {
                preferences.remove(Keys.avatarUri)
            } else {
                preferences[Keys.avatarUri] = profile.avatarUri
            }
        }
    }
}

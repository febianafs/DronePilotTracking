package com.example.dronepilottracking2026.data.repository

import com.example.dronepilottracking2026.data.local.ProfileDataStore
import com.example.dronepilottracking2026.data.model.PersonnelProfile
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dataStore: ProfileDataStore) {
    val profile: Flow<PersonnelProfile?> = dataStore.profile
    suspend fun save(profile: PersonnelProfile) = dataStore.save(profile)
}

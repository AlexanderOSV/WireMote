package com.myremote.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.profileDataStore by preferencesDataStore(name = "remote_profiles")

class ProfileStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val profilesKey = stringPreferencesKey("profiles")

    val profiles: Flow<List<RemoteProfile>> = context.profileDataStore.data.map { preferences: Preferences ->
        preferences[profilesKey]?.let { json.decodeFromString<List<RemoteProfile>>(it) } ?: emptyList()
    }

    suspend fun saveProfiles(profiles: List<RemoteProfile>) {
        context.profileDataStore.edit { preferences ->
            preferences[profilesKey] = json.encodeToString(profiles)
        }
    }
}

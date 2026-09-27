package com.hillah.alclogger.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hillah.alclogger.data.model.DrinkConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drink_preferences")

class DrinkPreferencesRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object PreferencesKeys {
        val DRINK_SLOTS = stringPreferencesKey("drink_slots_json")
    }

    val drinkSlotsFlow: Flow<List<DrinkConfig>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[PreferencesKeys.DRINK_SLOTS]
        if (jsonString.isNullOrBlank()) {
            DrinkConfig.defaultSlots()
        } else {
            try {
                json.decodeFromString<List<DrinkConfig>>(jsonString)
            } catch (e: Exception) {
                DrinkConfig.defaultSlots()
            }
        }
    }

    suspend fun updateSlot(config: DrinkConfig) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[PreferencesKeys.DRINK_SLOTS]
            val currentSlots = if (jsonString.isNullOrBlank()) {
                DrinkConfig.defaultSlots().toMutableList()
            } else {
                try {
                    json.decodeFromString<List<DrinkConfig>>(jsonString).toMutableList()
                } catch (e: Exception) {
                    DrinkConfig.defaultSlots().toMutableList()
                }
            }

            val index = currentSlots.indexOfFirst { it.slot == config.slot }
            if (index != -1) {
                currentSlots[index] = config
            } else {
                currentSlots.add(config)
            }

            preferences[PreferencesKeys.DRINK_SLOTS] = json.encodeToString(currentSlots)
        }
    }

    suspend fun resetToDefault() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DRINK_SLOTS] = json.encodeToString(DrinkConfig.defaultSlots())
        }
    }
}

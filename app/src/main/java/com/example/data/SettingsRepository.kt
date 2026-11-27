package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BoxerAppearance
import com.example.model.GloveItem
import com.example.model.PlayerEconomy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ring_ko_settings")

data class UserSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val playerName: String = "Retador"
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val COINS = intPreferencesKey("player_coins")
        val OWNED_GLOVES = stringSetPreferencesKey("owned_glove_ids")
        val EQUIPPED_GLOVE = stringPreferencesKey("equipped_glove_id")
        val PLAYER_NAME = stringPreferencesKey("player_name")
        val SKIN_PRESET_ID = stringPreferencesKey("skin_preset_id")
        val SKIN_TONE_IDX = intPreferencesKey("skin_tone_idx")
        val HAIR_COLOR_IDX = intPreferencesKey("hair_color_idx")
        val TRUNKS_COLOR_IDX = intPreferencesKey("trunks_color_idx")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            playerName = prefs[Keys.PLAYER_NAME]?.takeIf { it.isNotBlank() } ?: "Retador"
        )
    }

    val economyFlow: Flow<PlayerEconomy> = context.dataStore.data.map { prefs ->
        val owned = prefs[Keys.OWNED_GLOVES]?.takeIf { it.isNotEmpty() } ?: setOf("classic_red")
        val equipped = prefs[Keys.EQUIPPED_GLOVE]?.takeIf { owned.contains(it) } ?: "classic_red"
        PlayerEconomy(
            coins = prefs[Keys.COINS] ?: 200,
            ownedGloveIds = owned,
            equippedGloveId = equipped
        )
    }

    val appearanceFlow: Flow<BoxerAppearance> = context.dataStore.data.map { prefs ->
        BoxerAppearance(
            presetId = prefs[Keys.SKIN_PRESET_ID] ?: "arcade_classic",
            skinToneIndex = prefs[Keys.SKIN_TONE_IDX] ?: 1,
            hairColorIndex = prefs[Keys.HAIR_COLOR_IDX] ?: 1,
            trunksColorIndex = prefs[Keys.TRUNKS_COLOR_IDX] ?: 0
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setPlayerName(name: String) {
        val cleaned = name.trim().take(18).ifBlank { "Retador" }
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYER_NAME] = cleaned
        }
    }

    suspend fun addCoins(amount: Int) {
        if (amount <= 0) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.COINS] ?: 200
            prefs[Keys.COINS] = current + amount
        }
    }

    suspend fun buyOrEquipGlove(glove: GloveItem): Boolean {
        var success = false
        context.dataStore.edit { prefs ->
            val currentCoins = prefs[Keys.COINS] ?: 200
            val owned = (prefs[Keys.OWNED_GLOVES] ?: setOf("classic_red")) + "classic_red"
            if (owned.contains(glove.id)) {
                prefs[Keys.EQUIPPED_GLOVE] = glove.id
                success = true
            } else if (currentCoins >= glove.priceCoins) {
                prefs[Keys.COINS] = currentCoins - glove.priceCoins
                prefs[Keys.OWNED_GLOVES] = owned + glove.id
                prefs[Keys.EQUIPPED_GLOVE] = glove.id
                success = true
            }
        }
        return success
    }

    suspend fun saveAppearance(appearance: BoxerAppearance) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SKIN_PRESET_ID] = appearance.presetId
            prefs[Keys.SKIN_TONE_IDX] = appearance.skinToneIndex
            prefs[Keys.HAIR_COLOR_IDX] = appearance.hairColorIndex
            prefs[Keys.TRUNKS_COLOR_IDX] = appearance.trunksColorIndex
        }
    }
}

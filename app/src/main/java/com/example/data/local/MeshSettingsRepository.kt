package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.meshDataStore: DataStore<Preferences> by preferencesDataStore(name = "mesh_settings")

data class MeshSettings(
    val localNodeId: String,
    val callsign: String,
    val autoRelayEnabled: Boolean,
    val maxHopTtl: Int,
    val backgroundDiscoveryEnabled: Boolean,
    val themeMode: ThemeMode
)

class MeshSettingsRepository(private val context: Context) {
    private object Keys {
        val NODE_ID = stringPreferencesKey("local_node_id")
        val CALLSIGN = stringPreferencesKey("callsign")
        val AUTO_RELAY = booleanPreferencesKey("auto_relay_enabled")
        val MAX_HOPS = intPreferencesKey("max_hop_ttl")
        val DISCOVERY_ENABLED = booleanPreferencesKey("discovery_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private val defaultNodeId: String by lazy {
        "NODE-" + UUID.randomUUID().toString().replace("-", "").take(4).uppercase()
    }

    val settingsFlow: Flow<MeshSettings> = context.meshDataStore.data.map { prefs ->
        val nodeId = prefs[Keys.NODE_ID] ?: defaultNodeId
        val callsign = prefs[Keys.CALLSIGN] ?: android.os.Build.MODEL.take(16).ifBlank { nodeId }
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        MeshSettings(
            localNodeId = nodeId,
            callsign = callsign,
            autoRelayEnabled = prefs[Keys.AUTO_RELAY] ?: true,
            maxHopTtl = (prefs[Keys.MAX_HOPS] ?: 3).coerceIn(1, 5),
            backgroundDiscoveryEnabled = prefs[Keys.DISCOVERY_ENABLED] ?: true,
            themeMode = themeMode
        )
    }

    suspend fun ensureNodeIdInitialized(): String {
        var resolved = defaultNodeId
        context.meshDataStore.edit { prefs ->
            val existing = prefs[Keys.NODE_ID]
            if (existing == null) {
                prefs[Keys.NODE_ID] = resolved
            } else {
                resolved = existing
            }
        }
        return resolved
    }

    suspend fun updateCallsign(callsign: String) {
        val cleaned = callsign.trim().take(24)
        if (cleaned.isNotEmpty()) {
            context.meshDataStore.edit { it[Keys.CALLSIGN] = cleaned }
        }
    }

    suspend fun setAutoRelay(enabled: Boolean) {
        context.meshDataStore.edit { it[Keys.AUTO_RELAY] = enabled }
    }

    suspend fun setMaxHopTtl(hops: Int) {
        context.meshDataStore.edit { it[Keys.MAX_HOPS] = hops.coerceIn(1, 5) }
    }

    suspend fun setBackgroundDiscovery(enabled: Boolean) {
        context.meshDataStore.edit { it[Keys.DISCOVERY_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.meshDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}

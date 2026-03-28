package com.mazin.wasensai.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mazin.wasensai.ui.theme.AccentColor
import com.mazin.wasensai.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wa_sensai_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val ACCENT_KEY = stringPreferencesKey("accent_color")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.DARK.name)
    }

    val accentColor: Flow<AccentColor> = context.dataStore.data.map { prefs ->
        runCatching { AccentColor.valueOf(prefs[ACCENT_KEY] ?: AccentColor.GREEN.name) }
            .getOrDefault(AccentColor.GREEN)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setAccentColor(accent: AccentColor) {
        context.dataStore.edit { it[ACCENT_KEY] = accent.name }
    }
}

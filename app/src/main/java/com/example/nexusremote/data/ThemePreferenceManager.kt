package com.example.nexusremote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "control_xp_preferences")

class ThemePreferenceManager(private val context: Context) {
  companion object {
    val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")
  }

  val darkThemeFlow: Flow<Boolean> = context.dataStore.data
    .map { preferences ->
      preferences[DARK_THEME_KEY] ?: true // Default to true (night mode enabled)
    }

  suspend fun setDarkTheme(enabled: Boolean) {
    context.dataStore.edit { preferences ->
      preferences[DARK_THEME_KEY] = enabled
    }
  }
}

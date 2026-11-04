package com.peykr.app.data

import android.content.Context

enum class AppThemeMode {
  SYSTEM,
  LIGHT,
  DARK
}

class ThemePreferenceStore(context: Context) {
  private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  fun load(): AppThemeMode = preferences.getString(KEY, null)
    ?.let { value -> runCatching { AppThemeMode.valueOf(value) }.getOrNull() }
    ?: AppThemeMode.SYSTEM

  fun save(mode: AppThemeMode) {
    preferences.edit().putString(KEY, mode.name).apply()
  }

  private companion object {
    const val NAME = "peykr_preferences"
    const val KEY = "app_theme_mode"
  }
}

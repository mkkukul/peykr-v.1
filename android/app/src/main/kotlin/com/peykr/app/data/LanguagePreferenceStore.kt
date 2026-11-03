package com.peykr.app.data

import android.content.Context
import com.peykr.app.localization.AppLanguage

class LanguagePreferenceStore(context: Context) {
  private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  fun load(): AppLanguage = preferences.getString(KEY, null)
    ?.let { value -> runCatching { AppLanguage.valueOf(value) }.getOrNull() }
    ?: AppLanguage.TURKISH

  fun save(language: AppLanguage) {
    preferences.edit().putString(KEY, language.name).apply()
  }

  private companion object {
    const val NAME = "peykr_preferences"
    const val KEY = "app_language"
  }
}

package com.peykr.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.peykr.app.data.AppThemeMode
import com.peykr.app.data.LanguagePreferenceStore
import com.peykr.app.data.ThemePreferenceStore
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.splash.SplashScreen
import com.peykr.app.ui.theme.PEYKRTheme
import java.util.Locale

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val languageStore = LanguagePreferenceStore(this)
    val themeStore = ThemePreferenceStore(this)

    setContent {
      var language by remember { mutableStateOf(languageStore.load()) }
      var themeMode by remember { mutableStateOf(themeStore.load()) }

      val isSystemDark = isSystemInDarkTheme()
      val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
      }

      val localizedContext = remember(language) {
        val configuration = android.content.res.Configuration(resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language.languageTag))
        val configContext = createConfigurationContext(configuration)
        LocalizedActivityContext(this@MainActivity, configContext)
      }

      var isSplashFinished by remember { mutableStateOf(false) }

      CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalActivityResultRegistryOwner provides this@MainActivity,
      ) {
        PEYKRTheme(darkTheme = isDark) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!isSplashFinished) {
              SplashScreen(onLoaded = { isSplashFinished = true })
            } else {
              MainNavigation(
                language = language,
                onLanguageSelected = {
                  languageStore.save(it)
                  language = it
                },
                themeMode = themeMode,
                onThemeModeSelected = {
                  themeStore.save(it)
                  themeMode = it
                }
              )
            }
          }
        }
      }
    }
  }

  private class LocalizedActivityContext(
    baseActivity: FragmentActivity,
    private val localizedContext: Context
  ) : ContextWrapper(baseActivity), ActivityResultRegistryOwner {
    override fun getResources(): Resources = localizedContext.resources
    override fun getAssets(): AssetManager = localizedContext.assets
    override val activityResultRegistry: ActivityResultRegistry
      get() = (baseContext as FragmentActivity).activityResultRegistry
  }
}

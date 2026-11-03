package com.peykr.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.peykr.app.data.LanguagePreferenceStore
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.theme.PEYKRTheme
import java.util.Locale

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val languageStore = LanguagePreferenceStore(this)
    setContent {
      var language by remember { mutableStateOf(languageStore.load()) }
      val localizedContext = remember(language) {
        val configuration = android.content.res.Configuration(resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language.languageTag))
        createConfigurationContext(configuration)
      }
      CompositionLocalProvider(LocalContext provides localizedContext) {
        PEYKRTheme(darkTheme = true) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainNavigation(language, onLanguageSelected = { languageStore.save(it); language = it })
          }
        }
      }
    }
  }
}

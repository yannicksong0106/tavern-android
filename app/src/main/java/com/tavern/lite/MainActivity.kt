package com.tavern.lite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.ui.navigation.TavernNavGraph
import com.tavern.lite.ui.theme.TavernTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply language synchronously BEFORE setContent to avoid race condition
        val lang = runBlocking { settingsStore.languageFlow.first() }
        val localeList = when (lang) {
            "zh" -> LocaleListCompat.forLanguageTags("zh")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeList)

        setContent {
            val bubbleStyle by settingsStore.bubbleStyleFlow
                .collectAsStateWithLifecycle(initialValue = BubbleStyleConfig())

            TavernTheme(dynamicColor = bubbleStyle.dynamicColor) {
                TavernNavGraph()
            }
        }
    }
}

package com.tavern.lite

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash handler — writes full stacktrace to crash.log
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val crashDir = File(filesDir, "crash_logs")
                crashDir.mkdirs()
                File(crashDir, "crash.log").appendText(
                    "\n=== CRASH at $timestamp on thread ${thread.name} ===\n$sw\n"
                )
                Log.e("TavernCrash", "Uncaught exception on ${thread.name}", throwable)
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()

        // Apply language synchronously BEFORE setContent to avoid race condition
        val lang = runBlocking { settingsStore.languageFlow.first() }
        val localeList = when (lang) {
            "zh" -> LocaleListCompat.forLanguageTags("zh")
            "en" -> LocaleListCompat.forLanguageTags("en")
            "ja" -> LocaleListCompat.forLanguageTags("ja")
            "ko" -> LocaleListCompat.forLanguageTags("ko")
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

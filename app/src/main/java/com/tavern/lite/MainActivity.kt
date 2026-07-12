package com.tavern.lite

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tavern.lite.data.model.BubbleStyleConfig
import com.tavern.lite.data.store.SettingsStore
import com.tavern.lite.ui.navigation.TavernNavGraph
import com.tavern.lite.ui.theme.TavernTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

        // 从 SharedPreferences 同步读取缓存的语言（微秒级），避免 runBlocking DataStore 导致冷启动 ANR。
        // SettingsStore 每次写入语言时会同步更新此缓存（见 SettingsStore.saveLanguage）。
        val prefs = getSharedPreferences(LANGUAGE_CACHE_PREFS, Context.MODE_PRIVATE)
        val lang = prefs.getString(LANGUAGE_CACHE_KEY, "system") ?: "system"
        AppCompatDelegate.setApplicationLocales(langToLocaleList(lang))

        // 后台观察 DataStore：迁移场景或首次未同步时刷新缓存与 locale
        settingsStore.languageFlow
            .onEach { latest ->
                if (latest != lang) {
                    prefs.edit().putString(LANGUAGE_CACHE_KEY, latest).apply()
                    AppCompatDelegate.setApplicationLocales(langToLocaleList(latest))
                } else if (!prefs.contains(LANGUAGE_CACHE_KEY)) {
                    prefs.edit().putString(LANGUAGE_CACHE_KEY, latest).apply()
                }
            }
            .launchIn(lifecycleScope)

        setContent {
            val bubbleStyle by settingsStore.bubbleStyleFlow
                .collectAsStateWithLifecycle(initialValue = BubbleStyleConfig())

            TavernTheme(dynamicColor = bubbleStyle.dynamicColor) {
                TavernNavGraph()
            }
        }
    }

    private fun langToLocaleList(lang: String): LocaleListCompat = when (lang) {
        "zh" -> LocaleListCompat.forLanguageTags("zh")
        "en" -> LocaleListCompat.forLanguageTags("en")
        "ja" -> LocaleListCompat.forLanguageTags("ja")
        "ko" -> LocaleListCompat.forLanguageTags("ko")
        else -> LocaleListCompat.getEmptyLocaleList()
    }

    companion object {
        internal const val LANGUAGE_CACHE_PREFS = "tavern_startup_cache"
        internal const val LANGUAGE_CACHE_KEY = "language"
    }
}

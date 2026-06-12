package ai.telegram.android

import ai.telegram.android.ui.AiTelegramTheme
import ai.telegram.android.ui.AppThemeMode
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    private var pendingAndroidEntryIntent by mutableStateOf(AndroidEntryIntent())
    private var isActivityResumed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAndroidEntryIntent = intent.toAndroidEntryIntent()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(loadThemeMode()) }
            var interfaceLanguage by rememberSaveable {
                mutableStateOf(loadInterfaceLanguage())
            }
            val localizedContext = remember(interfaceLanguage) {
                this.withInterfaceLanguage(interfaceLanguage)
            }
            val localizedConfiguration = remember(interfaceLanguage) {
                Configuration(localizedContext.resources.configuration)
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration
            ) {
                AiTelegramTheme(themeMode = themeMode) {
                    TelegramClientApp(
                        pendingAndroidEntryIntent = pendingAndroidEntryIntent,
                        onAndroidEntryIntentHandled = { pendingAndroidEntryIntent = AndroidEntryIntent() },
                        isAppForeground = isActivityResumed,
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            saveThemeMode(mode)
                        },
                        interfaceLanguage = interfaceLanguage,
                        onInterfaceLanguageChange = { language ->
                            interfaceLanguage = language
                            saveInterfaceLanguage(language)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAndroidEntryIntent = intent.toAndroidEntryIntent()
    }

    private fun loadThemeMode(): AppThemeMode {
        val value = getPreferences(MODE_PRIVATE).getString(ThemeModeKey, AppThemeMode.System.name)
        return AppThemeMode.entries.firstOrNull { it.name == value } ?: AppThemeMode.System
    }

    private fun saveThemeMode(mode: AppThemeMode) {
        getPreferences(MODE_PRIVATE).edit {
            putString(ThemeModeKey, mode.name)
        }
    }

    private fun loadInterfaceLanguage(): InterfaceLanguage {
        val value = getPreferences(MODE_PRIVATE).getString(
            InterfaceLanguageKey,
            InterfaceLanguage.Vietnamese.name
        )
        return InterfaceLanguage.fromName(value)
    }

    private fun saveInterfaceLanguage(language: InterfaceLanguage) {
        getPreferences(MODE_PRIVATE).edit {
            putString(InterfaceLanguageKey, language.name)
        }
    }

    private companion object {
        const val ThemeModeKey = "theme_mode"
        const val InterfaceLanguageKey = "interface_language"
    }
}

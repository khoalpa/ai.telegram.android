package ai.telegram.android

import ai.telegram.android.ui.AiTelegramTheme
import ai.telegram.android.ui.AppThemeMode
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    private var pendingAndroidEntryIntent by mutableStateOf(AndroidEntryIntent())
    private var isActivityResumed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAndroidEntryIntent = intent.toAndroidEntryIntent()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(loadThemeMode()) }
            AiTelegramTheme(themeMode = themeMode) {
                TelegramClientApp(
                    pendingAndroidEntryIntent = pendingAndroidEntryIntent,
                    onAndroidEntryIntentHandled = { pendingAndroidEntryIntent = AndroidEntryIntent() },
                    isAppForeground = isActivityResumed,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        saveThemeMode(mode)
                    }
                )
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

    private companion object {
        const val ThemeModeKey = "theme_mode"
    }
}

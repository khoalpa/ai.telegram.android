package ai.telegram.android

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

enum class InterfaceLanguage(
    val tag: String,
    val labelRes: Int
) {
    Vietnamese("vi", R.string.vietnamese),
    English("en", R.string.english);

    fun locale(): Locale = Locale.forLanguageTag(tag)

    companion object {
        fun fromName(value: String?): InterfaceLanguage {
            return entries.firstOrNull { it.name == value } ?: Vietnamese
        }
    }
}

fun Context.withInterfaceLanguage(language: InterfaceLanguage): Context {
    val configuration = Configuration(resources.configuration)
    val locale = language.locale()
    Locale.setDefault(locale)
    configuration.setLocale(locale)
    configuration.setLocales(LocaleList(locale))
    val localizedContext = createConfigurationContext(configuration)
    return object : ContextWrapper(this) {
        override fun getAssets(): AssetManager = localizedContext.assets

        override fun getResources(): Resources = localizedContext.resources
    }
}

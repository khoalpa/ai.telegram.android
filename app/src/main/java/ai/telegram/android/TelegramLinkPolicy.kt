package ai.telegram.android

import java.util.Locale

const val UrlAnnotationTag = "URL"
val UrlPattern = Regex("""(?i)\b((?:https?://|tg://|www\.|t\.me/|telegram\.me/|telegram\.dog/)[^\s<]+)""")

fun normalizeUrl(url: String): String {
    val cleanUrl = url.trim()
    return when {
        cleanUrl.startsWith("www.", ignoreCase = true) -> "https://$cleanUrl"
        cleanUrl.startsWith("t.me/", ignoreCase = true) -> "https://$cleanUrl"
        cleanUrl.startsWith("telegram.me/", ignoreCase = true) -> "https://$cleanUrl"
        cleanUrl.startsWith("telegram.dog/", ignoreCase = true) -> "https://$cleanUrl"
        else -> cleanUrl
    }
}

fun isTelegramUrl(url: String): Boolean {
    val lower = url.trim().lowercase(Locale.ROOT)
    return lower.startsWith("tg://") ||
        lower.startsWith("https://t.me/") ||
        lower.startsWith("http://t.me/") ||
        lower.startsWith("https://telegram.me/") ||
        lower.startsWith("http://telegram.me/") ||
        lower.startsWith("https://telegram.dog/") ||
        lower.startsWith("http://telegram.dog/")
}

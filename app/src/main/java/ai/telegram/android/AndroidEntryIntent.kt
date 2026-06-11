package ai.telegram.android

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import java.net.URI
import java.util.Locale

data class AndroidEntryIntent(
    val id: Long = System.nanoTime(),
    val openChatId: Long? = null,
    val openCalls: Boolean = false,
    val telegramLink: String = "",
    val sharedText: String = "",
    val sharedUris: List<Uri> = emptyList(),
    val shortcut: AndroidShortcutTarget? = null
) {
    val hasShare: Boolean
        get() = sharedText.isNotBlank() || sharedUris.isNotEmpty()
}

enum class AndroidShortcutTarget {
    Chats,
    Search,
    Settings,
    Cache
}

fun Intent.toAndroidEntryIntent(): AndroidEntryIntent {
    val openChatId = getLongExtra(
        ai.telegram.android.notifications.TelegramNotificationManager.EXTRA_OPEN_CHAT_ID,
        0L
    ).takeIf { it != 0L }
    val openCalls = getBooleanExtra(
        ai.telegram.android.notifications.TelegramNotificationManager.EXTRA_OPEN_CALLS,
        false
    )
    val link = dataString
        .orEmpty()
        .takeIf { action == Intent.ACTION_VIEW }
        ?.let(AndroidEntryIntentPolicy::sanitizeTelegramLink)
        .orEmpty()
    val sharedUris = when (action) {
        Intent.ACTION_SEND -> singleSharedUri()?.let(::listOf).orEmpty()
        Intent.ACTION_SEND_MULTIPLE -> sharedUriList()
        else -> emptyList()
    }.filter { AndroidEntryIntentPolicy.isSafeSharedUriScheme(it.scheme) }
        .distinct()
        .take(AndroidEntryIntentPolicy.MaxSharedUris)
    val sharedText = when (action) {
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE -> getStringExtra(Intent.EXTRA_TEXT)
            .orEmpty()
            .let(AndroidEntryIntentPolicy::sanitizeSharedText)
        else -> ""
    }
    val shortcut = getStringExtra(ExtraShortcutTarget)?.let { raw ->
        AndroidShortcutTarget.entries.firstOrNull { it.name == raw }
    }
    return AndroidEntryIntent(
        openChatId = openChatId,
        openCalls = openCalls,
        telegramLink = link,
        sharedText = sharedText,
        sharedUris = sharedUris,
        shortcut = shortcut
    )
}

private fun Intent.singleSharedUri(): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
    }
}

private fun Intent.sharedUriList(): List<Uri> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            ?.mapNotNull { it as? Uri }
            .orEmpty()
    }
}

internal object AndroidEntryIntentPolicy {
    const val MaxTelegramLinkChars = 4096
    const val MaxSharedTextChars = 32_000
    const val MaxSharedUris = 10
    private val HttpSchemes = setOf("http", "https")
    private val TelegramHosts = setOf("t.me", "telegram.me", "telegram.dog")
    private val SafeSharedUriSchemes = setOf("content")

    fun sanitizeTelegramLink(raw: String): String {
        val value = raw.trim().take(MaxTelegramLinkChars)
        val uri = runCatching { URI(value) }.getOrNull() ?: return ""
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return ""
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        return when {
            scheme == "tg" -> value
            scheme in HttpSchemes && host in TelegramHosts -> value
            else -> ""
        }
    }

    fun sanitizeSharedText(raw: String): String {
        return raw.trim().take(MaxSharedTextChars)
    }

    fun isSafeSharedUriScheme(scheme: String?): Boolean {
        val cleanScheme = scheme?.lowercase(Locale.ROOT) ?: return false
        return cleanScheme in SafeSharedUriSchemes
    }
}

const val ExtraShortcutTarget = "ai.telegram.android.extra.SHORTCUT_TARGET"

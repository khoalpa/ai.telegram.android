package ai.telegram.android.data.telegram

import java.util.Locale

internal object TdLibCommandMapper {
    fun isInviteLink(input: String): Boolean {
        return input.contains("t.me/+", ignoreCase = true) ||
            input.contains("joinchat/", ignoreCase = true) ||
            input.startsWith("tg://join", ignoreCase = true)
    }

    fun usernameForJoin(input: String): String? {
        val username = input.trim()
            .removePrefix("@")
            .substringAfter("t.me/", input.trim())
            .substringBefore("?")
            .trim('/')
        return username.takeIf { it.isNotBlank() }
    }

    fun extractTelegramUsername(input: String): String? {
        val clean = input.trim()
        val domainFromResolve = Regex("""(?i)(?:domain|username)=([A-Za-z0-9_]{5,32})""")
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
        if (!domainFromResolve.isNullOrBlank()) return domainFromResolve

        val withoutAt = clean.removePrefix("@")
        val path = Regex("""(?i)^(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me)/(.+)$""")
            .find(withoutAt)
            ?.groupValues
            ?.getOrNull(1)
            ?: withoutAt
        val segments = path
            .substringBefore("?")
            .substringBefore("#")
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
        val username = if (segments.firstOrNull().equals("s", ignoreCase = true)) {
            segments.getOrNull(1)
        } else {
            segments.firstOrNull()
        } ?: return null
        if (username.lowercase(Locale.ROOT) in setOf("c", "joinchat", "+")) return null
        return username.takeIf { it.matches(Regex("""[A-Za-z0-9_]{5,32}""")) }
    }
}

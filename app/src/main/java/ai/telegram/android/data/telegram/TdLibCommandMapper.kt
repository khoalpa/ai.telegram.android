package ai.telegram.android.data.telegram

import java.util.Locale

internal object TdLibCommandMapper {
    private val TelegramHosts = setOf("t.me", "telegram.me", "telegram.dog")
    private val UsernamePattern = Regex("""[A-Za-z0-9_]{5,32}""")

    fun isInviteLink(input: String): Boolean {
        return Regex("""(?i)(?:t\.me|telegram\.me|telegram\.dog)/\+""").containsMatchIn(input) ||
            input.contains("joinchat/", ignoreCase = true) ||
            input.startsWith("tg://join", ignoreCase = true)
    }

    fun usernameForJoin(input: String): String? {
        return extractTelegramUsername(input)
    }

    fun extractTelegramUsername(input: String): String? {
        val clean = input.trim()
        val domainFromResolve = Regex("""(?i)(?:domain|username)=([A-Za-z0-9_]{5,32})""")
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
        if (!domainFromResolve.isNullOrBlank()) return domainFromResolve

        val withoutAt = clean.removePrefix("@").trim()
        val path = telegramPathOrNull(withoutAt) ?: withoutAt
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
        return username.takeIf { it.matches(UsernamePattern) }
    }

    private fun telegramPathOrNull(input: String): String? {
        val match = Regex("""(?i)^(?:https?://)?(?:www\.)?([^/]+)/(.+)$""")
            .find(input)
            ?: return null
        val host = match.groupValues.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()
        if (host !in TelegramHosts) return null
        return match.groupValues.getOrNull(2)
    }
}

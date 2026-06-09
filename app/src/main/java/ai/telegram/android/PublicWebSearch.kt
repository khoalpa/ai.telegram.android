package ai.telegram.android

import java.net.URLEncoder

enum class PublicSearchTarget {
    All,
    Channels,
    Groups,
    Bots
}

object PublicWebSearch {
    private const val SearchBaseUrl = "https://duckduckgo.com/?q="

    fun buildTelegramDiscoveryUrl(query: String, target: PublicSearchTarget): String {
        val encodedQuery = URLEncoder.encode(buildTelegramDiscoveryQuery(query, target), Charsets.UTF_8.name())
        return "$SearchBaseUrl$encodedQuery"
    }

    fun buildTelegramDiscoveryQuery(query: String, target: PublicSearchTarget): String {
        val cleanQuery = query.trim()
        val targetTerms = when (target) {
            PublicSearchTarget.All -> "(channel OR group OR bot OR chat)"
            PublicSearchTarget.Channels -> "(channel OR news OR updates)"
            PublicSearchTarget.Groups -> "(group OR chat OR community)"
            PublicSearchTarget.Bots -> "(bot OR inline bot)"
        }
        return "$cleanQuery $targetTerms (site:t.me OR site:telegram.me) -inurl:/s/"
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun telegramPublicLinkOrNull(input: String): String? {
        val cleanInput = input.trim().trimEnd('/', '.', ',', ';')
        if (cleanInput.isBlank()) return null
        val withoutScheme = cleanInput
            .removePrefix("https://")
            .removePrefix("http://")
        return when {
            cleanInput.startsWith("tg://", ignoreCase = true) -> cleanInput
            withoutScheme.startsWith("t.me/", ignoreCase = true) -> "https://$withoutScheme"
            withoutScheme.startsWith("telegram.me/", ignoreCase = true) -> "https://$withoutScheme"
            cleanInput.startsWith("@") && cleanInput.length > 1 -> {
                val username = cleanInput.drop(1).takeWhile { it.isLetterOrDigit() || it == '_' }
                username.takeIf { it.isNotBlank() }?.let { "https://t.me/$it" }
            }
            else -> null
        }
    }
}

fun String.publicSearchTargetOrDefault(): PublicSearchTarget {
    return PublicSearchTarget.entries.firstOrNull { it.name == this } ?: PublicSearchTarget.All
}

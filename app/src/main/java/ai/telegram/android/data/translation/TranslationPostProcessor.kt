package ai.telegram.android.data.translation

object TranslationPostProcessor {
    fun polish(sourceText: String, translatedText: String): String {
        if (translatedText.isBlank()) return translatedText
        var polished = translatedText.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?;:%])"), "$1")
            .replace(Regex("([\\(\\[\\{])\\s+"), "$1")
            .replace(Regex("\\s+([\\)\\]\\}])"), "$1")

        glossaryTerms(sourceText).forEach { term ->
            polished = restoreTerm(polished, term)
        }
        return polished
    }

    private fun glossaryTerms(sourceText: String): List<String> {
        val sourceLower = sourceText.lowercase()
        return TERMS.filter { term -> term.lowercase() in sourceLower }
    }

    private fun restoreTerm(text: String, term: String): String {
        if (text.contains(term, ignoreCase = false)) return text
        val pattern = Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE)
        return pattern.replace(text, term)
    }

    private val TERMS = listOf(
        "AI",
        "API",
        "APK",
        "Compose",
        "TDLib",
        "Telegram",
        "ML Kit",
        "token",
        "staking",
        "airdrop",
        "crypto",
        "blockchain",
        "Bitcoin",
        "Android",
        "Kotlin",
        "WorkManager"
    )
}

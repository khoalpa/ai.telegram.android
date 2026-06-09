package ai.telegram.android.data.translation

data class TranslationLanguagePair(
    val sourceLanguageCode: String,
    val sourceDisplayName: String,
    val targetLanguageCode: String,
    val targetDisplayName: String
) {
    val label: String
        get() = "$sourceDisplayName -> $targetDisplayName"

    val requiredModelLanguageCodes: List<String>
        get() = listOf(sourceLanguageCode).distinct()
}

object VietnameseTranslationPairs {
    val common: List<TranslationLanguagePair> = listOf(
        TranslationLanguagePair("en", "Anh", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("zh", "Trung", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("ja", "Nh\u1eadt", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("ko", "H\u00e0n", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("ru", "Nga", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("ar", "\u1ea2 R\u1eadp", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("hi", "Hindi", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("th", "Th\u00e1i", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("id", "Indonesia", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("es", "T\u00e2y Ban Nha", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("fr", "Ph\u00e1p", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("de", "\u0110\u1ee9c", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("pt", "B\u1ed3 \u0110\u00e0o Nha", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("it", "\u00dd", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("tr", "Th\u1ed5 Nh\u0129 K\u1ef3", "vi", "Vi\u1ec7t"),
        TranslationLanguagePair("uk", "Ukraina", "vi", "Vi\u1ec7t")
    )

    val requiredModelLanguageCodes: List<String>
        get() = common.flatMap { it.requiredModelLanguageCodes }.distinct()
}

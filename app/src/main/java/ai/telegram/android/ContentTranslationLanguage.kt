package ai.telegram.android

enum class ContentTranslationLanguage(
    val code: String,
    val labelRes: Int
) {
    Vietnamese("vi", R.string.vietnamese),
    English("en", R.string.english);

    companion object {
        const val DefaultCode = "vi"

        fun fromName(value: String?): ContentTranslationLanguage {
            return entries.firstOrNull { it.name == value } ?: Vietnamese
        }

        fun fromCode(value: String?): ContentTranslationLanguage {
            return entries.firstOrNull { it.code == value } ?: Vietnamese
        }
    }
}

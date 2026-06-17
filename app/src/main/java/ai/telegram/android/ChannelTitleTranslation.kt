package ai.telegram.android

internal fun fallbackVietnameseChannelTitle(title: String, targetLanguage: String): String {
    if (targetLanguage.substringBefore("-").lowercase() != "vi") return ""
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank() || cleanTitle.looksVietnameseTitle()) return ""

    KNOWN_CHANNEL_TITLE_TRANSLATIONS[cleanTitle]?.let { return it }

    return cleanTitle
        .replaceKnownTitleTerms()
        .takeIf { translated ->
            translated != cleanTitle && translated.any { char -> char.isLetter() }
        }
        .orEmpty()
}

internal fun normalizeTranslatedChannelTitle(originalTitle: String, translatedTitle: String): String {
    val normalized = translatedTitle
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace("（", "(")
        .replace("）", ")")
        .replace(Regex("\\s+([)\\],.!?;:%])"), "$1")
        .replace(Regex("([\\(\\[])\\s+"), "$1")
    return normalized.takeUnless {
        it.isBlank() || it.equals(originalTitle.trim(), ignoreCase = true)
    }.orEmpty()
}

internal fun shouldTranslateChannelTitle(title: String, targetLanguage: String): Boolean {
    val cleanTitle = title.trim()
    if (targetLanguage.substringBefore("-").lowercase() != "vi") return false
    if (cleanTitle.length < 2 || cleanTitle.looksVietnameseTitle()) return false
    return cleanTitle.any { char -> char.isLetter() && !char.isAsciiLetter() } ||
        cleanTitle.any { char -> char.isAsciiLetter() }
}

private fun String.replaceKnownTitleTerms(): String {
    var translated = this
    TITLE_TERM_TRANSLATIONS.forEach { (source, target) ->
        translated = translated.replace(source, target)
    }
    return translated
}

private fun String.looksVietnameseTitle(): Boolean {
    return any { it in VIETNAMESE_MARKS }
}

private fun Char.isAsciiLetter(): Boolean {
    return this in 'A'..'Z' || this in 'a'..'z'
}

private val KNOWN_CHANNEL_TITLE_TRANSLATIONS = mapOf(
    "国产精选(我们的世界)" to "Tuyển chọn nội địa (Thế giới của chúng ta)",
    "国产精选（我们的世界）" to "Tuyển chọn nội địa (Thế giới của chúng ta)"
)

private val TITLE_TERM_TRANSLATIONS = listOf(
    "国产" to "Nội địa",
    "精选" to "Tuyển chọn",
    "我们的世界" to "Thế giới của chúng ta",
    "世界" to "Thế giới",
    "新闻" to "Tin tức",
    "财经" to "Tài chính",
    "科技" to "Công nghệ",
    "全球" to "Toàn cầu",
    "官方" to "Chính thức",
    "频道" to "Kênh"
)

private const val VIETNAMESE_MARKS =
    "ăâđêôơưĂÂĐÊÔƠƯáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵÁÀẢÃẠẤẦẨẪẬẮẰẲẴẶÉÈẺẼẸẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌỐỒỔỖỘỚỜỞỠỢÚÙỦŨỤỨỪỬỮỰÝỲỶỸỴ"

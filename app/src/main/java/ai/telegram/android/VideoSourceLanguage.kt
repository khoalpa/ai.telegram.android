package ai.telegram.android

import androidx.annotation.StringRes

enum class VideoSourceLanguage(
    @param:StringRes val labelRes: Int,
    val speechLanguageTag: String
) {
    Auto(R.string.video_source_language_auto, ""),
    English(R.string.video_source_language_english, "en-US"),
    Vietnamese(R.string.video_source_language_vietnamese, "vi-VN"),
    Chinese(R.string.video_source_language_chinese, "zh-CN"),
    Japanese(R.string.video_source_language_japanese, "ja-JP"),
    Korean(R.string.video_source_language_korean, "ko-KR"),
    Russian(R.string.video_source_language_russian, "ru-RU"),
    Arabic(R.string.video_source_language_arabic, "ar-SA"),
    Hindi(R.string.video_source_language_hindi, "hi-IN"),
    Thai(R.string.video_source_language_thai, "th-TH"),
    Indonesian(R.string.video_source_language_indonesian, "id-ID"),
    Spanish(R.string.video_source_language_spanish, "es-ES"),
    French(R.string.video_source_language_french, "fr-FR"),
    German(R.string.video_source_language_german, "de-DE"),
    Portuguese(R.string.video_source_language_portuguese, "pt-BR"),
    Italian(R.string.video_source_language_italian, "it-IT"),
    Turkish(R.string.video_source_language_turkish, "tr-TR"),
    Ukrainian(R.string.video_source_language_ukrainian, "uk-UA");

    val sourceLanguageCode: String
        get() = speechLanguageTag.substringBefore("-").lowercase()

    companion object {
        fun fromName(name: String): VideoSourceLanguage {
            return entries.firstOrNull { it.name == name } ?: Auto
        }
    }
}

package ai.telegram.android.ui

import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationStatus

object MessagePrivacyPolicy {
    fun matchesReadableQuery(
        message: TelegramMessage,
        query: String,
        targetLanguage: String = message.translationTargetLanguage
    ): Boolean {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return true

        return readableText(message, targetLanguage = targetLanguage).contains(normalizedQuery, ignoreCase = true) ||
            message.author.contains(normalizedQuery, ignoreCase = true) ||
            message.chatTitle.contains(normalizedQuery, ignoreCase = true)
    }

    fun readableText(
        message: TelegramMessage,
        targetLanguage: String = message.translationTargetLanguage
    ): String {
        val translated = readableTranslatedText(message, targetLanguage = targetLanguage)
        if (translated.isNotBlank()) return translated

        return if (shouldRenderSourceText(message, targetLanguage = targetLanguage)) {
            message.originalText.trim()
        } else {
            ""
        }
    }

    fun readableTranslatedText(
        message: TelegramMessage,
        targetLanguage: String = message.translationTargetLanguage
    ): String {
        if (!message.translationTargetLanguage.equals(targetLanguage, ignoreCase = true)) return ""
        if (message.translationStatus != TranslationStatus.Ready) return ""
        val translated = message.translatedText.trim()
        if (translated.isBlank()) return ""

        val original = message.originalText.trim()
        val knownTargetLanguage = message.detectedLanguage.equals(
            message.translationTargetLanguage,
            ignoreCase = true
        )
        if (original.isNotBlank() && translated == original && !knownTargetLanguage) {
            return ""
        }
        return message.translatedText
    }

    fun isTelegramRestrictedNotice(message: TelegramMessage): Boolean {
        val text = listOf(message.originalText, message.translatedText)
            .joinToString(separator = " ")
            .trim()
        if (text.isBlank()) return false

        return text.contains("can't be displayed", ignoreCase = true) &&
            text.contains("telegram", ignoreCase = true) &&
            (
                text.contains("terms of service", ignoreCase = true) ||
                    text.contains("violated", ignoreCase = true)
                )
    }

    fun shouldRenderSourceText(
        message: TelegramMessage,
        targetLanguage: String = message.translationTargetLanguage
    ): Boolean {
        if (message.originalText.isBlank()) return false
        if (message.translationStatus == TranslationStatus.Hidden) return false
        return readableTranslatedText(message, targetLanguage = targetLanguage).isBlank()
    }

    fun shouldRenderSourceText(status: TranslationStatus): Boolean {
        return status == TranslationStatus.Pending ||
            status == TranslationStatus.Translating ||
            status == TranslationStatus.Failed
    }
}

package ai.telegram.android.ui

import ai.telegram.android.core.ContentNormalizer
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationStatus

object ChatMessageVisibilityPolicy {
    fun baseVisibleMessages(
        messages: List<TelegramMessage>,
        translatedOnly: Boolean,
        allowAdultContent: Boolean,
        hiddenHashes: Set<String>,
        targetLanguage: String = ""
    ): List<TelegramMessage> {
        val effectiveTargetLanguage = targetLanguage.ifBlank { null }
        return messages
            .filter { message ->
                isVisibleBeforeTranslationFilter(
                    message = message,
                    allowAdultContent = allowAdultContent,
                    hiddenHashes = hiddenHashes
                )
            }
            .filter { message ->
                !translatedOnly || shouldKeepInTranslatedOnlyView(
                    message,
                    targetLanguage = effectiveTargetLanguage ?: message.translationTargetLanguage
                )
            }
            .asReversed()
    }

    fun isHiddenByLocalPolicy(message: TelegramMessage, hiddenHashes: Set<String>): Boolean {
        return message.originalText.isNotBlank() &&
            ContentNormalizer.contentHash(message.originalText) in hiddenHashes
    }

    fun canHideByContent(message: TelegramMessage): Boolean {
        return message.originalText.isNotBlank() &&
            !MessagePrivacyPolicy.isTelegramRestrictedNotice(message)
    }

    private fun isVisibleBeforeTranslationFilter(
        message: TelegramMessage,
        allowAdultContent: Boolean,
        hiddenHashes: Set<String>
    ): Boolean {
        if (MessagePrivacyPolicy.isTelegramRestrictedNotice(message)) return true
        if (isHiddenByLocalPolicy(message, hiddenHashes)) return false
        if (message.translationStatus == TranslationStatus.Hidden) return allowAdultContent
        return true
    }

    private fun shouldKeepInTranslatedOnlyView(message: TelegramMessage, targetLanguage: String): Boolean {
        return (
            message.translationStatus == TranslationStatus.Ready &&
                message.translationTargetLanguage.equals(targetLanguage, ignoreCase = true)
            ) ||
            MessagePrivacyPolicy.shouldRenderSourceText(message, targetLanguage = targetLanguage)
    }
}

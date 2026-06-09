package ai.telegram.android.data.translation

import ai.telegram.android.data.BlacklistRepository
import ai.telegram.android.data.ChatRepository
import ai.telegram.android.data.MessageRepository
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationCacheRepository
import ai.telegram.android.data.TranslationFailureReason
import ai.telegram.android.data.TranslationStatus

data class TranslationProcessingResult(
    val completed: Boolean,
    val failureReason: TranslationFailureReason = TranslationFailureReason.None
) {
    val retryable: Boolean
        get() = failureReason in setOf(
            TranslationFailureReason.NetworkRequired,
            TranslationFailureReason.TransientError
        )
}

class TranslationProcessor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val blacklistRepository: BlacklistRepository,
    private val translationCacheRepository: TranslationCacheRepository,
    private val translationProvider: TranslationProvider,
    private val hiddenPreview: String = "Content hidden",
    private val failedPreview: String = "No translation available"
) {
    suspend fun process(message: TelegramMessage, targetLanguage: String = "vi"): TranslationProcessingResult {
        if (message.originalText.isBlank()) return TranslationProcessingResult(completed = true)

        if (blacklistRepository.isHidden(message.originalText)) {
            messageRepository.updateTranslation(
                message = message,
                translatedText = "",
                status = TranslationStatus.Hidden
            )
            chatRepository.touchLastMessage(message.chatId, hiddenPreview)
            return TranslationProcessingResult(completed = true)
        }

        messageRepository.updateTranslation(
            message = message,
            translatedText = "",
            status = TranslationStatus.Translating
        )

        var detectedLanguage = message.detectedLanguage
        val cached = translationCacheRepository.getOrTranslate(
            originalText = message.originalText,
            targetLanguage = targetLanguage,
            providerVersion = translationProvider.providerVersion
        ) {
            runCatching {
                translationProvider.translate(message.originalText, targetLanguage)
            }.getOrElse {
                TranslationResult.Unavailable(
                    reason = "Unable to translate this content.",
                    failureReason = TranslationFailureReason.TransientError
                )
            }
        }
        val translated = cached.translatedText
        detectedLanguage = cached.sourceLanguage.ifBlank { detectedLanguage }

        if (translated.isBlank()) {
            messageRepository.updateTranslation(
                message = message,
                translatedText = "",
                status = TranslationStatus.Failed,
                detectedLanguage = detectedLanguage,
                failureReason = cached.failureReason,
                targetLanguage = targetLanguage
            )
            chatRepository.touchLastMessage(message.chatId, message.originalText.ifBlank { failedPreview })
            return TranslationProcessingResult(
                completed = !cached.failureReason.isRetryable(),
                failureReason = cached.failureReason
            )
        }

        messageRepository.updateTranslation(
            message = message,
            translatedText = translated,
            status = TranslationStatus.Ready,
            detectedLanguage = detectedLanguage,
            failureReason = TranslationFailureReason.None,
            targetLanguage = targetLanguage
        )
        chatRepository.touchLastMessage(message.chatId, translated)
        return TranslationProcessingResult(completed = true)
    }

    private fun TranslationFailureReason.isRetryable(): Boolean {
        return this == TranslationFailureReason.NetworkRequired ||
            this == TranslationFailureReason.TransientError
    }
}

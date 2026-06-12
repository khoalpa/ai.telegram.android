package ai.telegram.android.data.translation

import ai.telegram.android.data.MessageRepository
import ai.telegram.android.data.NetworkMode
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.TranslationFailureReason
import ai.telegram.android.data.TranslationJobRepository
import ai.telegram.android.data.TranslationStatus
import ai.telegram.android.work.TranslationWorkScheduler
import android.content.Context

sealed interface TranslationQueueResult {
    data object Queued : TranslationQueueResult
    data class UpdatedOnly(val failureReason: TranslationFailureReason = TranslationFailureReason.None) : TranslationQueueResult
    data class Skipped(val reason: TranslationSkipReason) : TranslationQueueResult
    data class Blocked(val reason: TranslationQueueBlockReason) : TranslationQueueResult
}

enum class TranslationQueueBlockReason {
    BlankText,
    Roaming,
    MobileDataNeedsReadyPreflight
}

class TranslationQueue(
    private val context: Context,
    private val translationJobRepository: TranslationJobRepository,
    private val messageRepository: MessageRepository? = null,
    private val preflight: TranslationPreflight? = null,
    private val networkModeProvider: () -> NetworkMode = { NetworkMode.Wifi },
    private val targetLanguage: String = "vi"
) {
    suspend fun enqueue(message: TelegramMessage): TranslationQueueResult {
        return enqueueInternal(message)
    }

    suspend fun enqueueOnly(message: TelegramMessage): TranslationQueueResult {
        return enqueueInternal(message)
    }

    private suspend fun enqueueInternal(message: TelegramMessage): TranslationQueueResult {
        if (message.originalText.isBlank()) return TranslationQueueResult.Blocked(TranslationQueueBlockReason.BlankText)
        val preflightResult = preflight?.check(message.originalText, targetLanguage)
        when (val result = preflightResult) {
            is TranslationPreflightResult.Skip -> {
                val isAlreadyTargetLanguage = result.reason == TranslationSkipReason.AlreadyVietnamese ||
                    result.reason == TranslationSkipReason.AlreadyTargetLanguage
                messageRepository?.updateTranslation(
                    message = message,
                    translatedText = if (isAlreadyTargetLanguage) message.originalText else "",
                    status = if (isAlreadyTargetLanguage) TranslationStatus.Ready else TranslationStatus.Failed,
                    detectedLanguage = result.detectedLanguage.takeIf { it.isNotBlank() },
                    failureReason = if (isAlreadyTargetLanguage) {
                        TranslationFailureReason.None
                    } else {
                        TranslationFailureReason.NonTranslatable
                    },
                    targetLanguage = targetLanguage
                )
                return if (isAlreadyTargetLanguage) {
                    TranslationQueueResult.UpdatedOnly()
                } else {
                    TranslationQueueResult.Skipped(result.reason)
                }
            }
            is TranslationPreflightResult.MissingModel -> {
                messageRepository?.updateTranslation(
                    message = message,
                    translatedText = "",
                    status = TranslationStatus.Failed,
                    detectedLanguage = result.sourceLanguageCode,
                    failureReason = TranslationFailureReason.MissingModel,
                    targetLanguage = result.targetLanguageCode
                )
                return TranslationQueueResult.UpdatedOnly(TranslationFailureReason.MissingModel)
            }
            is TranslationPreflightResult.UnsupportedLanguage -> {
                messageRepository?.updateTranslation(
                    message = message,
                    translatedText = "",
                    status = TranslationStatus.Failed,
                    failureReason = TranslationFailureReason.UnsupportedLanguage,
                    targetLanguage = targetLanguage
                )
                return TranslationQueueResult.UpdatedOnly(TranslationFailureReason.UnsupportedLanguage)
            }
            TranslationPreflightResult.Ready,
            TranslationPreflightResult.UnknownSource,
            null -> Unit
        }
        if (networkModeProvider() == NetworkMode.Roaming) {
            return TranslationQueueResult.Blocked(TranslationQueueBlockReason.Roaming)
        }
        if (networkModeProvider() == NetworkMode.MobileData && preflightResult != TranslationPreflightResult.Ready) {
            return TranslationQueueResult.Blocked(TranslationQueueBlockReason.MobileDataNeedsReadyPreflight)
        }
        translationJobRepository.enqueue(message, targetLanguage)
        TranslationWorkScheduler.schedule(context)
        return TranslationQueueResult.Queued
    }
}

package ai.telegram.android.data.translation

import ai.telegram.android.data.TranslationFailureReason
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOnDeviceTranslationProvider(
    private val context: Context,
    private val reuseTranslators: Boolean = false
) : TranslationProvider, Closeable {
    override val providerVersion: String = "mlkit-translate-17.0.3-post-v1"
    private val translators = mutableMapOf<String, Translator>()

    override suspend fun translate(text: String, targetLanguage: String): TranslationResult {
        return translate(text = text, targetLanguage = targetLanguage, sourceLanguage = null)
    }

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String?
    ): TranslationResult {
        val target = TranslateLanguage.fromLanguageTag(targetLanguage)
            ?: return TranslationResult.Unavailable(
                reason = "Target language is not supported by ML Kit.",
                targetLanguageCode = targetLanguage,
                failureReason = TranslationFailureReason.UnsupportedLanguage
            )
        val sourceTag = sourceLanguage
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore("-")
            ?.lowercase()
            ?: detectSourceLanguage(text)
            ?: return TranslationResult.Unavailable(
                reason = "Could not detect the source language.",
                targetLanguageCode = targetLanguage,
                failureReason = TranslationFailureReason.UndetectedLanguage
            )
        val source = TranslateLanguage.fromLanguageTag(sourceTag)
            ?: return TranslationResult.Unavailable(
                reason = "Source language is not supported by ML Kit.",
                sourceLanguageCode = sourceTag,
                targetLanguageCode = targetLanguage,
                failureReason = TranslationFailureReason.UnsupportedLanguage
            )

        if (source == target) {
            return TranslationResult.Success(text, sourceLanguageCode = sourceTag)
        }

        val translator = translatorFor(source, target)
        return try {
            translator.downloadModelIfNeeded(
                DownloadConditions.Builder()
                    .requireWifi()
                    .build()
            ).await()
            TranslationResult.Success(
                text = translator.translate(text).await(),
                sourceLanguageCode = sourceTag
            )
        } catch (error: Throwable) {
            TranslationResult.Unavailable(
                reason = error.message ?: "Unable to translate on device.",
                sourceLanguageCode = sourceTag,
                targetLanguageCode = targetLanguage,
                failureReason = error.translationFailureReason()
            )
        } finally {
            if (!reuseTranslators) {
                translator.close()
            }
        }
    }

    override fun close() {
        translators.values.forEach { translator -> runCatching { translator.close() } }
        translators.clear()
    }

    private suspend fun detectSourceLanguage(text: String): String? {
        val languageIdentifier = LanguageIdentification.getClient()
        return try {
            languageIdentifier.identifyLanguage(text).await()
                .takeUnless { it == "und" }
        } catch (_: Throwable) {
            null
        } finally {
            languageIdentifier.close()
        }
    }

    private fun translatorFor(source: String, target: String): Translator {
        if (!reuseTranslators) {
            return Translation.getClient(translatorOptions(source, target))
        }
        val key = "$source:$target"
        return translators.getOrPut(key) {
            Translation.getClient(translatorOptions(source, target))
        }
    }

    private fun translatorOptions(source: String, target: String): TranslatorOptions {
        return TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
    }
}

private fun Throwable.translationFailureReason(): TranslationFailureReason {
    val message = message.orEmpty().lowercase()
    return when {
        "model" in message || "download" in message -> TranslationFailureReason.MissingModel
        "network" in message || "wifi" in message || "internet" in message -> TranslationFailureReason.NetworkRequired
        else -> TranslationFailureReason.TransientError
    }
}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}

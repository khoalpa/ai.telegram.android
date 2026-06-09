package ai.telegram.android.data.translation

import ai.telegram.android.data.TranslationFailureReason
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class TranslationSkipReason {
    UrlOnly,
    HandleOnly,
    EmojiOnly,
    NumericOnly,
    CodeLike,
    TooShort,
    AlreadyVietnamese
}

sealed interface TranslationPreflightResult {
    data object Ready : TranslationPreflightResult
    data class Skip(val reason: TranslationSkipReason, val detectedLanguage: String = "") : TranslationPreflightResult
    data class MissingModel(
        val sourceLanguageCode: String,
        val targetLanguageCode: String,
        val missingLanguageCodes: Set<String>
    ) : TranslationPreflightResult
    data class UnsupportedLanguage(val languageCode: String) : TranslationPreflightResult
    data object UnknownSource : TranslationPreflightResult
}

object TranslationSkipClassifier {
    fun classify(text: String, targetLanguage: String = "vi"): TranslationPreflightResult.Skip? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val compact = trimmed.replace(Regex("\\s+"), " ")
        val withoutUrls = compact.replace(URL_PATTERN, "").trim()
        return when {
            URL_PATTERN.matches(compact) -> TranslationPreflightResult.Skip(TranslationSkipReason.UrlOnly)
            HANDLE_PATTERN.matches(compact) -> TranslationPreflightResult.Skip(TranslationSkipReason.HandleOnly)
            NUMERIC_PATTERN.matches(compact) -> TranslationPreflightResult.Skip(TranslationSkipReason.NumericOnly)
            compact.length <= 2 -> TranslationPreflightResult.Skip(TranslationSkipReason.TooShort)
            compact.length <= 4 && compact.count(Char::isLetterOrDigit) <= 2 -> {
                TranslationPreflightResult.Skip(TranslationSkipReason.TooShort)
            }
            withoutUrls.isBlank() -> TranslationPreflightResult.Skip(TranslationSkipReason.UrlOnly)
            compact.isEmojiOnly() -> TranslationPreflightResult.Skip(TranslationSkipReason.EmojiOnly)
            compact.isCodeLike() -> TranslationPreflightResult.Skip(TranslationSkipReason.CodeLike)
            targetLanguage == "vi" && compact.looksVietnamese() -> {
                TranslationPreflightResult.Skip(TranslationSkipReason.AlreadyVietnamese, detectedLanguage = "vi")
            }
            else -> null
        }
    }

    private fun String.isEmojiOnly(): Boolean {
        val meaningful = filterNot { it.isWhitespace() }
        if (meaningful.isEmpty()) return false
        return meaningful.none { it.isLetterOrDigit() }
    }

    private fun String.isCodeLike(): Boolean {
        val codeMarkers = listOf("{", "}", "<", "/>", "==", "=>", "::", "```")
        val markerHits = codeMarkers.count { contains(it) }
        val symbolRatio = count { !it.isLetterOrDigit() && !it.isWhitespace() }.toFloat() / length.coerceAtLeast(1)
        val letterOrDigitCount = count(Char::isLetterOrDigit).coerceAtLeast(1)
        val asciiLetterOrDigitRatio = count { it.isAsciiLetterOrDigit() }.toFloat() / letterOrDigitCount
        return markerHits >= 2 || (length >= 12 && symbolRatio > 0.45f && asciiLetterOrDigitRatio > 0.8f)
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean {
        return this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
    }

    private fun String.looksVietnamese(): Boolean {
        if (any { it in VIETNAMESE_DIACRITICS }) return true
        val words = lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() }
        if (words.size < 3) return false
        val commonHits = words.count { it in COMMON_VIETNAMESE_WORDS }
        return commonHits >= 2
    }

    private val URL_PATTERN = Regex("""(?i)^\s*((?:https?://|tg://|www\.|t\.me/|telegram\.me/)\S+)\s*$""")
    private val HANDLE_PATTERN = Regex("""^\s*[@#][\p{L}\p{N}_]{2,64}\s*$""")
    private val NUMERIC_PATTERN = Regex("""^\s*[\d\s+().,\-:%/]+$""")
    private const val VIETNAMESE_DIACRITICS =
        "ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ"
    private val COMMON_VIETNAMESE_WORDS = setOf(
        "va", "và", "la", "là", "cua", "của", "cho", "voi", "với", "toi", "tôi",
        "ban", "bạn", "minh", "mình", "khong", "không", "co", "có", "duoc", "được",
        "trong", "nay", "này", "nhung", "nhưng", "neu", "nếu", "da", "đã"
    )
}

open class TranslationPreflight(
    private val downloadedLanguageCodes: suspend () -> Set<String>,
    private val detectSourceLanguage: suspend (String) -> String?
) {
    suspend fun check(text: String, targetLanguage: String): TranslationPreflightResult {
        TranslationSkipClassifier.classify(text, targetLanguage)?.let { return it }
        val target = TranslateLanguage.fromLanguageTag(targetLanguage)
            ?: return TranslationPreflightResult.UnsupportedLanguage(targetLanguage)
        val sourceLanguage = detectSourceLanguage(text) ?: return TranslationPreflightResult.UnknownSource
        val source = TranslateLanguage.fromLanguageTag(sourceLanguage)
            ?: return TranslationPreflightResult.UnsupportedLanguage(sourceLanguage)
        if (source == target) {
            return TranslationPreflightResult.Skip(TranslationSkipReason.AlreadyVietnamese, detectedLanguage = sourceLanguage)
        }
        val downloaded = downloadedLanguageCodes()
        val required = requiredDownloadedModelCodes(sourceLanguage, targetLanguage)
        val missing = required - downloaded
        return if (missing.isEmpty()) {
            TranslationPreflightResult.Ready
        } else {
            TranslationPreflightResult.MissingModel(
                sourceLanguageCode = sourceLanguage,
                targetLanguageCode = targetLanguage,
                missingLanguageCodes = missing
            )
        }
    }

    private fun requiredDownloadedModelCodes(sourceLanguage: String, targetLanguage: String): Set<String> {
        return if (targetLanguage == VIETNAMESE_LANGUAGE_CODE) {
            setOf(sourceLanguage)
        } else {
            setOf(sourceLanguage, targetLanguage)
        }
    }

    private companion object {
        const val VIETNAMESE_LANGUAGE_CODE = "vi"
    }
}

class MlKitTranslationPreflight(
    private val modelManager: MlKitModelManager
) : TranslationPreflight(
    downloadedLanguageCodes = { modelManager.cachedDownloadedTranslationModelCodes() },
    detectSourceLanguage = { text -> MlKitSourceLanguageDetector.detect(text) }
)

private object MlKitSourceLanguageDetector {
    suspend fun detect(text: String): String? {
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
}

private suspend fun MlKitModelManager.cachedDownloadedTranslationModelCodes(): Set<String> {
    return TranslationModelCodeCache.get(this)
}

private object TranslationModelCodeCache {
    private var cachedAtMillis: Long = 0L
    private var cachedCodes: Set<String> = emptySet()

    suspend fun get(modelManager: MlKitModelManager): Set<String> {
        val now = System.currentTimeMillis()
        if (cachedCodes.isNotEmpty() && now - cachedAtMillis < CACHE_TTL_MILLIS) {
            return cachedCodes
        }
        cachedCodes = modelManager.downloadedTranslationModelCodes()
        cachedAtMillis = now
        return cachedCodes
    }

    private const val CACHE_TTL_MILLIS = 60_000L
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

fun TranslationPreflightResult.failureReason(): TranslationFailureReason {
    return when (this) {
        is TranslationPreflightResult.MissingModel -> TranslationFailureReason.MissingModel
        is TranslationPreflightResult.UnsupportedLanguage -> TranslationFailureReason.UnsupportedLanguage
        TranslationPreflightResult.UnknownSource -> TranslationFailureReason.UndetectedLanguage
        TranslationPreflightResult.Ready -> TranslationFailureReason.None
        is TranslationPreflightResult.Skip -> if (this.reason == TranslationSkipReason.AlreadyVietnamese) {
            TranslationFailureReason.None
        } else {
            TranslationFailureReason.NonTranslatable
        }
    }
}

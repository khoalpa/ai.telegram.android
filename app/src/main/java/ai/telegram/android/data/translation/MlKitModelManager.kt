package ai.telegram.android.data.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MlKitTranslationModelInfo(
    val languageCode: String,
    val displayName: String,
    val providerName: String,
    val backendName: String,
    val storageKey: String,
    val modelName: String,
    val modelHash: String?,
    val modelType: String,
    val isBaseModel: Boolean
)

data class MlKitModelDownloadProgress(
    val operationLabel: String,
    val languageCode: String,
    val languageDisplayName: String,
    val currentStep: Int,
    val totalSteps: Int,
    val skippedAlreadyDownloadedCount: Int,
    val isAlreadyDownloaded: Boolean
)

class MlKitModelOperationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MlKitModelManager {
    private val modelManager: RemoteModelManager = RemoteModelManager.getInstance()

    suspend fun downloadedTranslationModels(): List<MlKitTranslationModelInfo> {
        return modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .await()
            .map { model -> model.toSafeInfo() }
            .sortedBy { it.displayName }
    }

    suspend fun downloadedTranslationModelCodes(): Set<String> {
        return modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .await()
            .mapNotNull { model ->
                runCatching { model.language }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
            .toSet()
    }

    suspend fun deleteTranslationModel(languageCode: String) {
        val model = remoteModelFor(languageCode)
        try {
            modelManager.deleteDownloadedModel(model).await()
        } catch (error: Throwable) {
            throw MlKitModelOperationException(
                "Kh\u00f4ng x\u00f3a \u0111\u01b0\u1ee3c ${displayNameFor(languageCode)}: ${error.readableMessage()}",
                error
            )
        }
    }

    suspend fun downloadTranslationModel(languageCode: String) {
        val model = remoteModelFor(languageCode)
        try {
            modelManager.download(
                model,
                DownloadConditions.Builder().build()
            ).await()
        } catch (error: Throwable) {
            throw MlKitModelOperationException(
                "Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c ${displayNameFor(languageCode)}: ${error.readableMessage()}",
                error
            )
        }
    }

    suspend fun downloadTranslationPair(
        pair: TranslationLanguagePair,
        onProgress: (MlKitModelDownloadProgress) -> Unit = {}
    ) {
        downloadModelList(
            operationLabel = pair.label,
            languageCodes = pair.requiredModelLanguageCodes,
            onProgress = onProgress
        )
    }

    suspend fun downloadTranslationPairs(
        pairs: List<TranslationLanguagePair>,
        onProgress: (MlKitModelDownloadProgress) -> Unit = {}
    ) {
        downloadModelList(
            operationLabel = pairs.joinToString { it.label },
            languageCodes = pairs.flatMap { it.requiredModelLanguageCodes }.distinct(),
            onProgress = onProgress
        )
    }

    private suspend fun downloadModelList(
        operationLabel: String,
        languageCodes: List<String>,
        onProgress: (MlKitModelDownloadProgress) -> Unit
    ) {
        val downloadedCodes = downloadedTranslationModelCodes()
        ModelDownloadResumePlanner.plan(languageCodes, downloadedCodes).forEach { step ->
            onProgress(
                MlKitModelDownloadProgress(
                    operationLabel = operationLabel,
                    languageCode = step.languageCode,
                    languageDisplayName = displayNameFor(step.languageCode),
                    currentStep = step.currentStep,
                    totalSteps = step.totalSteps,
                    skippedAlreadyDownloadedCount = step.skippedAlreadyDownloadedCount,
                    isAlreadyDownloaded = step.isAlreadyDownloaded
                )
            )
            if (step.isAlreadyDownloaded) return@forEach
            downloadTranslationModel(step.languageCode)
        }
    }

    private fun displayNameFor(languageCode: String): String {
        return when (languageCode) {
            "en" -> "Anh (en)"
            "zh" -> "Trung (zh)"
            "ru" -> "Nga (ru)"
            "ja" -> "Nh\u1eadt (ja)"
            "ko" -> "H\u00e0n (ko)"
            "ar" -> "\u1ea2 R\u1eadp (ar)"
            "hi" -> "Hindi (hi)"
            "th" -> "Th\u00e1i (th)"
            "id" -> "Indonesia (id)"
            "es" -> "T\u00e2y Ban Nha (es)"
            "fr" -> "Ph\u00e1p (fr)"
            "de" -> "\u0110\u1ee9c (de)"
            "pt" -> "B\u1ed3 \u0110\u00e0o Nha (pt)"
            "it" -> "\u00dd (it)"
            "tr" -> "Th\u1ed5 Nh\u0129 K\u1ef3 (tr)"
            "uk" -> "Ukraina (uk)"
            "vi" -> "Vi\u1ec7t (vi)"
            else -> languageCode.uppercase()
        }
    }

    private fun remoteModelFor(languageCode: String): TranslateRemoteModel {
        val supportedLanguage = TranslateLanguage.fromLanguageTag(languageCode)
            ?: throw MlKitModelOperationException("ML Kit kh\u00f4ng h\u1ed7 tr\u1ee3 m\u00e3 ng\u00f4n ng\u1eef: $languageCode")
        return TranslateRemoteModel.Builder(supportedLanguage).build()
    }

    private fun TranslateRemoteModel.toSafeInfo(): MlKitTranslationModelInfo {
        val languageCode = runCatching { language }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"

        return MlKitTranslationModelInfo(
            languageCode = languageCode,
            displayName = displayNameFor(languageCode),
            providerName = "ML Kit Translation",
            backendName = runCatching { getModelNameForBackend() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "mlkit-translate-$languageCode",
            storageKey = runCatching { getUniqueModelNameForPersist() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "translate-$languageCode",
            modelName = runCatching { modelName }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: languageCode,
            modelHash = runCatching { modelHash }
                .getOrNull()
                ?.takeIf { it.isNotBlank() },
            modelType = runCatching { modelType.name }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "TRANSLATE",
            isBaseModel = runCatching { isBaseModel }.getOrDefault(false)
        )
    }
}

private fun Throwable.readableMessage(): String {
    return localizedMessage
        ?.takeIf { it.isNotBlank() }
        ?: message?.takeIf { it.isNotBlank() }
        ?: javaClass.simpleName
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

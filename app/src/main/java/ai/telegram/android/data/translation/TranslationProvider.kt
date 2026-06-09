package ai.telegram.android.data.translation

import ai.telegram.android.data.TranslationFailureReason

interface TranslationProvider {
    val providerVersion: String

    suspend fun translate(text: String, targetLanguage: String): TranslationResult

    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String?
    ): TranslationResult = translate(text, targetLanguage)
}

sealed interface TranslationResult {
    data class Success(
        val text: String,
        val sourceLanguageCode: String? = null
    ) : TranslationResult
    data class Unavailable(
        val reason: String,
        val sourceLanguageCode: String? = null,
        val targetLanguageCode: String? = null,
        val failureReason: TranslationFailureReason = TranslationFailureReason.TransientError
    ) : TranslationResult
}

class LocalTranslationProvider : TranslationProvider {
    override val providerVersion: String = "local-pack-v1"

    override suspend fun translate(text: String, targetLanguage: String): TranslationResult {
        if (targetLanguage != "vi") {
            return TranslationResult.Unavailable(
                reason = "Chưa có gói dịch cho ngôn ngữ giao diện hiện tại.",
                targetLanguageCode = targetLanguage,
                failureReason = TranslationFailureReason.UnsupportedLanguage
            )
        }

        val known = sampleTranslations[text]
        return if (known != null) {
            TranslationResult.Success(known)
        } else {
            TranslationResult.Unavailable(
                reason = "Chưa có gói dịch trên thiết bị cho nội dung này.",
                targetLanguageCode = targetLanguage,
                failureReason = TranslationFailureReason.MissingModel
            )
        }
    }

    private companion object {
        val sampleTranslations = mapOf(
            "Open-source translation models are becoming smaller and faster for mobile devices." to
                "Các mô hình dịch mã nguồn mở đang nhỏ hơn và nhanh hơn cho thiết bị di động.",
            "Limited offer. Join this private group now and double your portfolio." to
                "Ưu đãi có hạn. Tham gia nhóm riêng này ngay và nhân đôi danh mục của bạn.",
            "Compose previews make it easier to tune responsive layouts before wiring data." to
                "Compose Preview giúp tinh chỉnh bố cục đáp ứng dễ hơn trước khi nối dữ liệu."
        )
    }
}

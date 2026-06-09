package ai.telegram.android.data.translation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationPreflightTest {
    @Test
    fun check_doesNotRequireVietnameseTargetModelWhenSourceModelIsDownloaded() = runBlocking {
        val preflight = TranslationPreflight(
            downloadedLanguageCodes = { setOf("en") },
            detectSourceLanguage = { "en" }
        )

        assertEquals(
            TranslationPreflightResult.Ready,
            preflight.check("Open-source translation models are getting smaller.", "vi")
        )
    }

    @Test
    fun check_reportsMissingSourceModelForVietnameseTarget() = runBlocking {
        val preflight = TranslationPreflight(
            downloadedLanguageCodes = { emptySet() },
            detectSourceLanguage = { "en" }
        )

        assertEquals(
            TranslationPreflightResult.MissingModel(
                sourceLanguageCode = "en",
                targetLanguageCode = "vi",
                missingLanguageCodes = setOf("en")
            ),
            preflight.check("Open-source translation models are getting smaller.", "vi")
        )
    }
}

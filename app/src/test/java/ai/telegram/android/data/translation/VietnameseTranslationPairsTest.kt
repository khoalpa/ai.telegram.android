package ai.telegram.android.data.translation

import ai.telegram.android.VideoSourceLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class VietnameseTranslationPairsTest {
    @Test
    fun commonPairs_coverAudioSourcesToVietnamese() {
        val pairs = VietnameseTranslationPairs.common

        assertEquals(
            listOf(
                "en",
                "zh",
                "ja",
                "ko",
                "ru",
                "ar",
                "hi",
                "th",
                "id",
                "es",
                "fr",
                "de",
                "pt",
                "it",
                "tr",
                "uk"
            ),
            pairs.map { it.sourceLanguageCode }
        )
        assertEquals(List(16) { "vi" }, pairs.map { it.targetLanguageCode })
    }

    @Test
    fun commonPairs_matchNonVietnameseVideoSourceLanguages() {
        val sourceLanguageCodes = VideoSourceLanguage.entries
            .filterNot { it == VideoSourceLanguage.Auto || it == VideoSourceLanguage.Vietnamese }
            .map { it.sourceLanguageCode }

        assertEquals(
            sourceLanguageCodes,
            VietnameseTranslationPairs.common.map { it.sourceLanguageCode }
        )
    }

    @Test
    fun requiredModelLanguageCodes_areDistinctForBulkDownload() {
        assertEquals(
            listOf(
                "en",
                "zh",
                "ja",
                "ko",
                "ru",
                "ar",
                "hi",
                "th",
                "id",
                "es",
                "fr",
                "de",
                "pt",
                "it",
                "tr",
                "uk"
            ),
            VietnameseTranslationPairs.requiredModelLanguageCodes
        )
    }

    @Test
    fun pairRequiredModelLanguageCodes_onlyPreloadSourceModelForVietnameseTarget() {
        assertEquals(
            listOf("ja"),
            VietnameseTranslationPairs.common.first { it.sourceLanguageCode == "ja" }.requiredModelLanguageCodes
        )
    }
}

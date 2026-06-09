package ai.telegram.android.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadResumePlannerTest {
    @Test
    fun plan_marksAlreadyDownloadedModelsAsSkippedForResume() {
        val plan = ModelDownloadResumePlanner.plan(
            languageCodes = listOf("en", "vi"),
            downloadedLanguageCodes = setOf("en")
        )

        assertEquals(2, plan.size)
        assertTrue(plan[0].isAlreadyDownloaded)
        assertEquals(1, plan[0].skippedAlreadyDownloadedCount)
        assertFalse(plan[1].isAlreadyDownloaded)
        assertEquals(1, plan[1].skippedAlreadyDownloadedCount)
    }

    @Test
    fun plan_deduplicatesLanguageCodesBeforeCountingProgress() {
        val plan = ModelDownloadResumePlanner.plan(
            languageCodes = listOf("en", "vi", "vi", "zh"),
            downloadedLanguageCodes = setOf("vi")
        )

        assertEquals(listOf("en", "vi", "zh"), plan.map { it.languageCode })
        assertEquals(listOf(1, 2, 3), plan.map { it.currentStep })
        assertEquals(List(3) { 3 }, plan.map { it.totalSteps })
    }
}

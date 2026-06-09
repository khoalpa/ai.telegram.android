package ai.telegram.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationFailurePolicyTest {
    @Test
    fun retryableFailures_matchWorkerRetryReasons() {
        assertTrue(TranslationFailureReason.NetworkRequired.isRetryableFailure())
        assertTrue(TranslationFailureReason.TransientError.isRetryableFailure())
        assertFalse(TranslationFailureReason.MissingModel.isRetryableFailure())
        assertFalse(TranslationFailureReason.UnsupportedLanguage.isRetryableFailure())
        assertFalse(TranslationFailureReason.UndetectedLanguage.isRetryableFailure())
        assertFalse(TranslationFailureReason.NonTranslatable.isRetryableFailure())
        assertFalse(TranslationFailureReason.None.isRetryableFailure())
    }

    @Test
    fun retryableFailureNames_areStableForRoomQueries() {
        assertEquals(
            listOf(
                TranslationFailureReason.NetworkRequired.name,
                TranslationFailureReason.TransientError.name
            ),
            retryableTranslationFailureNames()
        )
    }
}

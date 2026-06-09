package ai.telegram.android.work

import ai.telegram.android.data.TranslationJobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRetryPolicyTest {
    @Test
    fun failedTranslation_isRequeuedBeforeFinalAttempt() {
        assertEquals(TranslationJobStatus.Pending, TranslationRetryPolicy.nextFailedStatus(0))
        assertEquals(TranslationJobStatus.Pending, TranslationRetryPolicy.nextFailedStatus(1))
    }

    @Test
    fun failedTranslation_isMarkedFailedAtAttemptLimit() {
        assertEquals(TranslationJobStatus.Failed, TranslationRetryPolicy.nextFailedStatus(2))
        assertEquals(TranslationJobStatus.Failed, TranslationRetryPolicy.nextFailedStatus(3))
    }

    @Test
    fun workerRetriesOnlyWhenPendingJobsRemain() {
        assertFalse(TranslationRetryPolicy.shouldRetry(0))
        assertTrue(TranslationRetryPolicy.shouldRetry(1))
    }
}

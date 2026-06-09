package ai.telegram.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContentNormalizerTest {
    @Test
    fun normalize_trimsLowercasesAndCollapsesWhitespace() {
        val normalized = ContentNormalizer.normalize("  Limited\t OFFER.\nJoin   now  ")

        assertEquals("limited offer. join now", normalized)
    }

    @Test
    fun contentHash_isStableForEquivalentContent() {
        val first = ContentNormalizer.contentHash("Limited offer. Join this private group now.")
        val second = ContentNormalizer.contentHash("  limited   OFFER.   join this private group now. ")

        assertEquals(first, second)
    }

    @Test
    fun contentHash_changesWhenContentChanges() {
        val first = ContentNormalizer.contentHash("Limited offer. Join this private group now.")
        val second = ContentNormalizer.contentHash("Limited offer. Join this public group now.")

        assertNotEquals(first, second)
    }
}

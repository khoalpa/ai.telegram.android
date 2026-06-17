package ai.telegram.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TranslationJobIdentityTest {
    @Test
    fun key_isStableForSameMessageContentAndLanguage() {
        val first = TranslationJobIdentity.key("42:7", "hash-a", "VI")
        val second = TranslationJobIdentity.key("42:7", "hash-a", "vi")

        assertEquals(first, second)
    }

    @Test
    fun key_changesWhenMessageContentChanges() {
        val original = TranslationJobIdentity.key("42:7", "hash-a", "vi")
        val edited = TranslationJobIdentity.key("42:7", "hash-b", "vi")

        assertNotEquals(original, edited)
    }
}

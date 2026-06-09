package ai.telegram.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidEntryIntentPolicyTest {
    @Test
    fun sanitizeTelegramLink_allowsTelegramSchemesAndHosts() {
        assertEquals(
            "tg://resolve?domain=example",
            AndroidEntryIntentPolicy.sanitizeTelegramLink("  tg://resolve?domain=example  ")
        )
        assertEquals(
            "https://t.me/example",
            AndroidEntryIntentPolicy.sanitizeTelegramLink("https://t.me/example")
        )
        assertEquals(
            "http://telegram.me/example",
            AndroidEntryIntentPolicy.sanitizeTelegramLink("http://telegram.me/example")
        )
    }

    @Test
    fun sanitizeTelegramLink_rejectsNonTelegramOrMalformedLinks() {
        assertEquals("", AndroidEntryIntentPolicy.sanitizeTelegramLink("https://evil.example/t.me/channel"))
        assertEquals("", AndroidEntryIntentPolicy.sanitizeTelegramLink("https://t.me.evil.example/channel"))
        assertEquals("", AndroidEntryIntentPolicy.sanitizeTelegramLink("javascript:alert(1)"))
        assertEquals("", AndroidEntryIntentPolicy.sanitizeTelegramLink("not a uri"))
    }

    @Test
    fun sanitizeSharedText_trimsAndCapsExternalText() {
        val oversized = " ".repeat(4) + "a".repeat(AndroidEntryIntentPolicy.MaxSharedTextChars + 20)

        val sanitized = AndroidEntryIntentPolicy.sanitizeSharedText(oversized)

        assertEquals(AndroidEntryIntentPolicy.MaxSharedTextChars, sanitized.length)
        assertTrue(sanitized.all { it == 'a' })
    }

    @Test
    fun isSafeSharedUriScheme_allowsOnlyLocalShareSchemes() {
        assertTrue(AndroidEntryIntentPolicy.isSafeSharedUriScheme("content"))
        assertTrue(AndroidEntryIntentPolicy.isSafeSharedUriScheme("FILE"))
        assertFalse(AndroidEntryIntentPolicy.isSafeSharedUriScheme("http"))
        assertFalse(AndroidEntryIntentPolicy.isSafeSharedUriScheme("https"))
        assertFalse(AndroidEntryIntentPolicy.isSafeSharedUriScheme(null))
    }
}

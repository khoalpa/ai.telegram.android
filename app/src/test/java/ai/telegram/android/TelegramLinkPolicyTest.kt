package ai.telegram.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramLinkPolicyTest {
    @Test
    fun normalizeUrl_addsHttpsToCommonBareLinks() {
        assertEquals("https://www.example.com", normalizeUrl("www.example.com"))
        assertEquals("https://t.me/example", normalizeUrl("  t.me/example  "))
        assertEquals("https://telegram.me/example", normalizeUrl("telegram.me/example"))
        assertEquals("https://telegram.dog/example", normalizeUrl("telegram.dog/example"))
    }

    @Test
    fun normalizeUrl_preservesExistingSchemes() {
        assertEquals("https://t.me/example", normalizeUrl("https://t.me/example"))
        assertEquals("tg://resolve?domain=example", normalizeUrl("tg://resolve?domain=example"))
    }

    @Test
    fun isTelegramUrl_allowsTelegramSchemesAndHosts() {
        assertTrue(isTelegramUrl("tg://resolve?domain=example"))
        assertTrue(isTelegramUrl(" https://t.me/example "))
        assertTrue(isTelegramUrl("http://telegram.me/example"))
        assertTrue(isTelegramUrl("https://telegram.dog/example"))
    }

    @Test
    fun isTelegramUrl_rejectsLookalikeHostsAndGenericUrls() {
        assertFalse(isTelegramUrl("https://t.me.evil.example/channel"))
        assertFalse(isTelegramUrl("https://evil.example/t.me/channel"))
        assertFalse(isTelegramUrl("https://www.example.com"))
    }

    @Test
    fun urlPattern_findsTelegramAndWebLinks() {
        val links = UrlPattern.findAll("Read https://t.me/example and telegram.dog/channel")
            .map { it.value }
            .toList()

        assertEquals(listOf("https://t.me/example", "telegram.dog/channel"), links)
    }
}

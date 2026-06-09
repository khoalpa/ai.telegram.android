package ai.telegram.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicWebSearchTest {
    @Test
    fun buildTelegramDiscoveryQuery_keepsUserQueryAndLimitsToPublicTelegramSites() {
        val query = PublicWebSearch.buildTelegramDiscoveryQuery("android dev vietnam", PublicSearchTarget.Channels)

        assertTrue(query.contains("android dev vietnam"))
        assertTrue(query.contains("site:t.me"))
        assertTrue(query.contains("site:telegram.me"))
        assertTrue(query.contains("channel"))
    }

    @Test
    fun buildTelegramDiscoveryUrl_encodesSearchQuery() {
        val url = PublicWebSearch.buildTelegramDiscoveryUrl("ai tools", PublicSearchTarget.Bots)

        assertTrue(url.startsWith("https://duckduckgo.com/?q="))
        assertTrue(url.contains("ai+tools"))
        assertTrue(url.contains("site%3At.me"))
    }

    @Test
    fun telegramPublicLinkOrNull_normalizesCommonInputs() {
        assertEquals("https://t.me/example_channel", PublicWebSearch.telegramPublicLinkOrNull("@example_channel"))
        assertEquals("https://t.me/example_channel", PublicWebSearch.telegramPublicLinkOrNull("t.me/example_channel"))
        assertEquals("tg://resolve?domain=example", PublicWebSearch.telegramPublicLinkOrNull("tg://resolve?domain=example"))
    }

    @Test
    fun telegramPublicLinkOrNull_doesNotTreatPlainKeywordsAsLinks() {
        assertEquals(null, PublicWebSearch.telegramPublicLinkOrNull("crypto"))
        assertEquals(null, PublicWebSearch.telegramPublicLinkOrNull("android vietnam"))
    }
}

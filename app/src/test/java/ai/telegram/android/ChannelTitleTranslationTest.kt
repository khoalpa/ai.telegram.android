package ai.telegram.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelTitleTranslationTest {
    @Test
    fun fallbackVietnameseChannelTitle_translatesKnownChineseChannelTitle() {
        assertEquals(
            "Tuyển chọn nội địa (Thế giới của chúng ta)",
            fallbackVietnameseChannelTitle("国产精选(我们的世界)", "vi")
        )
    }

    @Test
    fun fallbackVietnameseChannelTitle_ignoresVietnameseAndNonVietnameseTargets() {
        assertEquals("", fallbackVietnameseChannelTitle("Tin tức Việt Nam", "vi"))
        assertEquals("", fallbackVietnameseChannelTitle("国产精选(我们的世界)", "en"))
    }

    @Test
    fun normalizeTranslatedChannelTitle_hidesUnchangedTranslations() {
        assertEquals("", normalizeTranslatedChannelTitle("AI News", " AI News "))
        assertEquals("Tin tức AI", normalizeTranslatedChannelTitle("AI News", " Tin   tức AI "))
    }

    @Test
    fun shouldTranslateChannelTitle_targetsNonVietnameseChannelNames() {
        assertTrue(shouldTranslateChannelTitle("国产精选(我们的世界)", "vi"))
        assertTrue(shouldTranslateChannelTitle("AI News Global", "vi"))
        assertFalse(shouldTranslateChannelTitle("Tin tức Việt Nam", "vi"))
        assertFalse(shouldTranslateChannelTitle("AI News Global", "en"))
    }
}

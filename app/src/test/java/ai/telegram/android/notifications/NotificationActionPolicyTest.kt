package ai.telegram.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionPolicyTest {
    @Test
    fun trimsReplyText() {
        assertEquals("hello", NotificationActionPolicy.sanitizeReplyText("  hello  "))
    }

    @Test
    fun blankReplyTextStaysBlank() {
        assertEquals("", NotificationActionPolicy.sanitizeReplyText("   "))
        assertEquals("", NotificationActionPolicy.sanitizeReplyText(null))
    }

    @Test
    fun limitsReplyTextLength() {
        val longText = "x".repeat(NotificationActionPolicy.MaxReplyTextChars + 20)

        val sanitized = NotificationActionPolicy.sanitizeReplyText(longText)

        assertEquals(NotificationActionPolicy.MaxReplyTextChars, sanitized.length)
        assertTrue(sanitized.all { it == 'x' })
    }
}

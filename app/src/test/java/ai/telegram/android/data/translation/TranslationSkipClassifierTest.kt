package ai.telegram.android.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationSkipClassifierTest {
    @Test
    fun classify_skipsUrlOnlyContent() {
        val result = TranslationSkipClassifier.classify("https://t.me/example")

        assertEquals(TranslationSkipReason.UrlOnly, result?.reason)
    }

    @Test
    fun classify_skipsAlreadyVietnameseContent() {
        val result = TranslationSkipClassifier.classify("Mình đã nhận được nội dung này rồi.")

        assertEquals(TranslationSkipReason.AlreadyVietnamese, result?.reason)
        assertEquals("vi", result?.detectedLanguage)
    }

    @Test
    fun classify_skipsCodeLikeContent() {
        val result = TranslationSkipClassifier.classify("if (value == null) { return; }")

        assertEquals(TranslationSkipReason.CodeLike, result?.reason)
    }

    @Test
    fun classify_allowsNormalForeignText() {
        val result = TranslationSkipClassifier.classify("Open-source translation models are getting smaller.")

        assertNull(result)
    }

    @Test
    fun classify_allowsEmojiHeavyCjkText() {
        val result = TranslationSkipClassifier.classify(
            "\uD83D\uDE00\uD83D\uDE00\uD83D\uDD25 \u4E94\u5E74\u54C1\u724C\u4FE1\u8A89\uFF0C" +
                "\u503C\u5F97\u4FE1\u8D56\uFF01 \u6BCF\u65E5\u7EA2\u5305\u96E8"
        )

        assertNull(result)
    }
}

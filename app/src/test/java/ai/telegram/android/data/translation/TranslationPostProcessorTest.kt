package ai.telegram.android.data.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationPostProcessorTest {
    @Test
    fun polishRemovesSpacesAroundBrackets() {
        val polished = TranslationPostProcessor.polish(
            sourceText = "Telegram",
            translatedText = " (  Telegram  )  [ test ]  { ok } "
        )

        assertEquals("(Telegram) [test] {ok}", polished)
    }
}

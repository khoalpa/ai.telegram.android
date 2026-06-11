package ai.telegram.android.data.translation

import ai.telegram.android.data.TranslationFailureReason
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class VideoSubtitleGeneratorDeviceTest {
    @Test
    fun generate_returnsUnavailableWhenSpeechRecognitionServiceIsMissing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assumeFalse(SpeechRecognizer.isRecognitionAvailable(context))

        val generator = VideoSubtitleGenerator(
            context = context,
            translationProvider = unavailableTranslationProvider,
            subtitleCacheRepository = null
        )

        val result = generator.generate(
            videoFile = File(context.cacheDir, "missing-video.mp4"),
            fileId = 123,
            mode = VideoSubtitleMode.Source
        )

        assertTrue(result is VideoSubtitleResult.Unavailable)
        assertTrue((result as VideoSubtitleResult.Unavailable).reason.contains("nhan dang"))
    }

    private val unavailableTranslationProvider = object : TranslationProvider {
        override val providerVersion: String = "device-test-unavailable"

        override suspend fun translate(text: String, targetLanguage: String): TranslationResult {
            return TranslationResult.Unavailable(
                reason = "Translation unavailable in device test.",
                targetLanguageCode = targetLanguage,
                failureReason = TranslationFailureReason.UnsupportedLanguage
            )
        }
    }
}

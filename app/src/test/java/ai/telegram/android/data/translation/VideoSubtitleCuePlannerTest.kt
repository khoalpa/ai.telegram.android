package ai.telegram.android.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSubtitleCuePlannerTest {
    @Test
    fun createsTimedCuesFromWordTiming() {
        val cues = VideoSubtitleCuePlanner.sourceCues(
            RecognizedSpeech(
                text = "Hello world. Next line.",
                words = listOf(
                    RecognizedWord("Hello", 0L),
                    RecognizedWord("world.", 420L),
                    RecognizedWord("Next", 2_200L),
                    RecognizedWord("line.", 2_650L)
                )
            ),
            durationMs = 5_000L
        )

        assertEquals(2, cues.size)
        assertEquals(VideoSubtitleCue(0L, 2_080L, "Hello world."), cues[0])
        assertEquals(VideoSubtitleCue(2_200L, 3_300L, "Next line."), cues[1])
    }

    @Test
    fun distributesSentencesWhenWordTimingIsMissing() {
        val cues = VideoSubtitleCuePlanner.sourceCues(
            RecognizedSpeech(text = "First sentence. Second sentence."),
            durationMs = 6_000L
        )

        assertEquals(2, cues.size)
        assertEquals("First sentence.", cues[0].text)
        assertEquals("Second sentence.", cues[1].text)
        assertEquals(0L, cues.first().startMs)
        assertEquals(6_000L, cues.last().endMs)
        assertTrue(cues.all { it.endMs > it.startMs })
    }

    @Test
    fun chunksLongTranscriptIntoReadableCues() {
        val cues = VideoSubtitleCuePlanner.sourceCues(
            RecognizedSpeech(
                text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen"
            ),
            durationMs = 8_000L
        )

        assertEquals(2, cues.size)
        assertEquals("one two three four five six seven eight nine ten eleven twelve", cues[0].text)
        assertEquals("thirteen fourteen", cues[1].text)
    }
}

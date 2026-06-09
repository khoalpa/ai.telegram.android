package ai.telegram.android.data.translation

internal data class RecognizedSpeech(
    val text: String = "",
    val words: List<RecognizedWord> = emptyList(),
    val errorReason: String? = null
)

internal data class RecognizedWord(
    val text: String,
    val timestampMs: Long
)

internal object VideoSubtitleCuePlanner {
    fun sourceCues(speech: RecognizedSpeech, durationMs: Long): List<VideoSubtitleCue> {
        val timedWords = speech.words
            .filter { it.text.isNotBlank() }
            .sortedBy { it.timestampMs }
            .takeIf { parts ->
                parts.size >= 2 && parts.zipWithNext().any { (first, second) -> second.timestampMs > first.timestampMs }
            }

        return timedWords?.toTimedCues(durationMs)
            ?: splitSentences(speech.text).toDistributedCues(durationMs)
    }
}

internal fun String.cleanSubtitleText(): String {
    return trim().replace(Regex("\\s+"), " ")
}

private fun List<RecognizedWord>.toTimedCues(durationMs: Long): List<VideoSubtitleCue> {
    val groups = mutableListOf<List<RecognizedWord>>()
    var current = mutableListOf<RecognizedWord>()
    forEach { word ->
        current += word
        val elapsed = current.last().timestampMs - current.first().timestampMs
        val endsSentence = word.text.endsWith(".") ||
            word.text.endsWith("?") ||
            word.text.endsWith("!") ||
            word.text.endsWith("\u3002")
        if (endsSentence || current.size >= MAX_WORDS_PER_CUE || elapsed >= MAX_CUE_DURATION_MS) {
            groups += current
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) groups += current

    return groups.mapIndexedNotNull { index, group ->
        val startMs = group.first().timestampMs.coerceAtLeast(0L)
        val nextStartMs = groups.getOrNull(index + 1)?.firstOrNull()?.timestampMs
        val estimatedEndMs = group.last().timestampMs + ESTIMATED_WORD_DURATION_MS
        val endMs = (nextStartMs?.minus(120L) ?: estimatedEndMs)
            .coerceAtLeast(startMs + MIN_CUE_DURATION_MS)
            .coerceAtMost(durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        val text = group.joinToString(" ") { it.text }.cleanSubtitleText()
        if (text.isBlank()) null else VideoSubtitleCue(startMs, endMs, text)
    }
}

private fun List<String>.toDistributedCues(durationMs: Long): List<VideoSubtitleCue> {
    if (isEmpty()) return emptyList()
    val safeDuration = durationMs.takeIf { it > 0L } ?: (size * 3_000L)
    val totalWords = sumOf { sentence -> sentence.split(Regex("\\s+")).count { it.isNotBlank() } }
        .coerceAtLeast(size)
    var cursor = 0L
    return mapIndexed { index, sentence ->
        val wordCount = sentence.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        val span = if (index == lastIndex) {
            safeDuration - cursor
        } else {
            (safeDuration * wordCount / totalWords).coerceAtLeast(MIN_CUE_DURATION_MS)
        }
        val end = if (index == lastIndex) safeDuration else (cursor + span).coerceAtMost(safeDuration)
        VideoSubtitleCue(
            startMs = cursor,
            endMs = end.coerceAtLeast(cursor + MIN_CUE_DURATION_MS),
            text = sentence.cleanSubtitleText()
        ).also {
            cursor = end
        }
    }
}

private fun splitSentences(text: String): List<String> {
    return text
        .split(Regex("(?<=[.!?\u3002])\\s+"))
        .map { it.cleanSubtitleText() }
        .filter { it.isNotBlank() }
        .flatMap { sentence ->
            val words = sentence.split(Regex("\\s+"))
            if (words.size <= MAX_WORDS_PER_CUE) {
                listOf(sentence)
            } else {
                words.chunked(MAX_WORDS_PER_CUE).map { it.joinToString(" ") }
            }
        }
}

private const val MAX_WORDS_PER_CUE = 12
private const val MAX_CUE_DURATION_MS = 5_000L
private const val MIN_CUE_DURATION_MS = 1_000L
private const val ESTIMATED_WORD_DURATION_MS = 650L

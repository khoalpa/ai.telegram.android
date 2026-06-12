package ai.telegram.android.data.translation

import ai.telegram.android.data.VideoSubtitleCacheRepository
import ai.telegram.android.data.local.AppDatabase
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface VideoSubtitleResult {
    data class Success(
        val sourceText: String,
        val sourceLanguageCode: String = "",
        val cues: List<VideoSubtitleCue>
    ) : VideoSubtitleResult

    data class Unavailable(val reason: String) : VideoSubtitleResult
}

data class VideoSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class VideoSubtitleMode {
    Source,
    Vietnamese
}

class VideoSubtitleGenerator(
    private val context: Context,
    private val translationProvider: TranslationProvider = MlKitOnDeviceTranslationProvider(context),
    private val subtitleCacheRepository: VideoSubtitleCacheRepository? =
        VideoSubtitleCacheRepository(AppDatabase.get(context.applicationContext))
) {
    suspend fun generate(
        videoFile: File,
        fileId: Int = 0,
        mode: VideoSubtitleMode = VideoSubtitleMode.Source,
        sourceLanguageCode: String = ""
    ): VideoSubtitleResult {
        val normalizedSourceLanguage = sourceLanguageCode.normalizedSpeechLanguageCode()
        return try {
            when (mode) {
                VideoSubtitleMode.Source -> generateSourceTranscript(videoFile, fileId, normalizedSourceLanguage)
                VideoSubtitleMode.Vietnamese -> generateVietnameseTranscript(videoFile, fileId, normalizedSourceLanguage)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            VideoSubtitleResult.Unavailable(
                error.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: "Khong tao duoc phu de video."
            )
        }
    }

    private suspend fun generateSourceTranscript(
        videoFile: File,
        fileId: Int,
        sourceLanguageCode: String
    ): VideoSubtitleResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return VideoSubtitleResult.Unavailable("STT tu audio video can Android 13/API 33 tro len.")
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return VideoSubtitleResult.Unavailable("Thiet bi chua co dich vu nhan dang giong noi kha dung.")
        }
        if (!videoFile.exists() || videoFile.length() <= 0L) {
            return VideoSubtitleResult.Unavailable("File video chua san sang.")
        }

        subtitleCacheRepository?.get(
            fileId = fileId,
            videoFile = videoFile,
            targetLanguage = sourceTranscriptCacheKey(sourceLanguageCode),
            providerVersion = SOURCE_TRANSCRIPT_PROVIDER_VERSION
        )?.let { cache ->
            if (cache.cues.isNotEmpty()) {
                val cachedSourceLanguageCode = cache.sourceLanguageCode.ifBlank {
                    sourceLanguageCode.baseLanguageCode().ifBlank {
                        detectSourceLanguage(cache.sourceText).orEmpty()
                    }
                }
                return VideoSubtitleResult.Success(
                    sourceText = cache.sourceText,
                    sourceLanguageCode = cachedSourceLanguageCode,
                    cues = cache.cues
                )
            }
        }

        val durationMs = videoFile.durationMs()
        val audioSource = withContext(Dispatchers.IO) {
            runCatching { VideoPcmAudioPipe.open(videoFile) }.getOrNull()
        } ?: return VideoSubtitleResult.Unavailable("Khong trich duoc audio tu video.")

        val speechTimeoutMs = ((durationMs.takeIf { it > 0L } ?: 60_000L) + 30_000L)
            .coerceIn(45_000L, 180_000L)
        val speech = withTimeoutOrNull(speechTimeoutMs) {
            recognize(audioSource, sourceLanguageCode)
        } ?: RecognizedSpeech(errorReason = "Nhan dang giong noi qua thoi gian cho.")
        speech.errorReason?.let { reason ->
            return VideoSubtitleResult.Unavailable(reason)
        }
        if (speech.text.isBlank()) {
            return VideoSubtitleResult.Unavailable("Khong nhan dang duoc loi thoai trong video.")
        }

        val sourceCues = VideoSubtitleCuePlanner.sourceCues(speech, durationMs)
        if (sourceCues.isEmpty()) {
            return VideoSubtitleResult.Unavailable("Khong tao duoc moc thoi gian phu de.")
        }
        val detectedSourceLanguageCode = sourceLanguageCode.baseLanguageCode().ifBlank {
            detectSourceLanguage(speech.text).orEmpty()
        }

        subtitleCacheRepository?.put(
            fileId = fileId,
            videoFile = videoFile,
            targetLanguage = sourceTranscriptCacheKey(sourceLanguageCode),
            providerVersion = SOURCE_TRANSCRIPT_PROVIDER_VERSION,
            sourceText = speech.text,
            sourceLanguageCode = detectedSourceLanguageCode,
            cues = sourceCues
        )
        return VideoSubtitleResult.Success(
            sourceText = speech.text,
            sourceLanguageCode = detectedSourceLanguageCode,
            cues = sourceCues
        )
    }

    private suspend fun generateVietnameseTranscript(
        videoFile: File,
        fileId: Int,
        sourceLanguageCode: String
    ): VideoSubtitleResult {
        val sourceResult = generateSourceTranscript(videoFile, fileId, sourceLanguageCode)
        if (sourceResult !is VideoSubtitleResult.Success) return sourceResult
        val sourceCues = sourceResult.cues
        val sourceLanguageTag = sourceResult.sourceLanguageCode.ifBlank {
            sourceLanguageCode.baseLanguageCode()
        }
        if (sourceLanguageTag == VIETNAMESE_LANGUAGE_CODE) {
            return sourceResult
        }
        val translationProviderVersion = "$SOURCE_TRANSCRIPT_PROVIDER_VERSION:${sourceLanguageCode.cacheLanguageSuffix()}+${translationProvider.providerVersion}"

        subtitleCacheRepository?.get(
            fileId = fileId,
            videoFile = videoFile,
            targetLanguage = VIETNAMESE_LANGUAGE_CODE,
            providerVersion = translationProviderVersion
        )?.let { cache ->
            if (cache.cues.isNotEmpty()) {
                val cachedSourceLanguageCode = cache.sourceLanguageCode.ifBlank {
                    sourceLanguageTag.ifBlank {
                        detectSourceLanguage(cache.sourceText).orEmpty()
                    }
                }
                return VideoSubtitleResult.Success(
                    sourceText = cache.sourceText,
                    sourceLanguageCode = cachedSourceLanguageCode,
                    cues = cache.cues
                )
            }
        }

        val translatedCues = mutableListOf<VideoSubtitleCue>()
        var translatedSourceLanguageCode = sourceResult.sourceLanguageCode
        for (cue in sourceCues) {
            when (
                val translation = translationProvider.translate(
                    text = cue.text,
                    targetLanguage = VIETNAMESE_LANGUAGE_CODE,
                    sourceLanguage = sourceLanguageTag
                )
            ) {
                is TranslationResult.Success -> {
                    if (translatedSourceLanguageCode.isBlank()) {
                        translatedSourceLanguageCode = translation.sourceLanguageCode.orEmpty()
                    }
                    val text = TranslationPostProcessor.polish(cue.text, translation.text)
                    if (text.isNotBlank()) {
                        translatedCues += cue.copy(text = text)
                    }
                }
                is TranslationResult.Unavailable -> return VideoSubtitleResult.Unavailable(translation.reason)
            }
        }
        if (translatedCues.isEmpty()) {
            return VideoSubtitleResult.Unavailable("Khong tao duoc transcript tieng Viet.")
        }

        subtitleCacheRepository?.put(
            fileId = fileId,
            videoFile = videoFile,
            targetLanguage = VIETNAMESE_LANGUAGE_CODE,
            providerVersion = translationProviderVersion,
            sourceText = sourceResult.sourceText,
            sourceLanguageCode = translatedSourceLanguageCode,
            cues = translatedCues
        )
        return VideoSubtitleResult.Success(
            sourceText = sourceResult.sourceText,
            sourceLanguageCode = translatedSourceLanguageCode,
            cues = translatedCues
        )
    }

    private suspend fun detectSourceLanguage(text: String): String? {
        if (text.isBlank()) return null
        val languageIdentifier = runCatching { LanguageIdentification.getClient() }
            .getOrNull()
            ?: return null
        return try {
            languageIdentifier.identifyLanguage(text).await()
                .takeUnless { it == "und" }
        } catch (_: Throwable) {
            null
        } finally {
            languageIdentifier.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognize(audioSource: PcmAudioSource, sourceLanguageCode: String): RecognizedSpeech {
        return suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            var recognizer: SpeechRecognizer? = null

            fun finish(speech: RecognizedSpeech) {
                if (continuation.isActive) {
                    continuation.resume(speech)
                }
                mainHandler.post {
                    recognizer?.destroy()
                    audioSource.close()
                }
            }

            mainHandler.post {
                runCatching {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        private val segments = mutableListOf<String>()
                        private val words = mutableListOf<RecognizedWord>()

                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit

                        override fun onError(error: Int) {
                            finish(RecognizedSpeech(errorReason = speechRecognizerErrorReason(error)))
                        }

                        override fun onResults(results: Bundle?) {
                            results?.bestRecognitionText()?.let { segments += it }
                            words += results?.recognitionWords().orEmpty()
                            finish(
                                RecognizedSpeech(
                                    text = segments.joinToString(" ").cleanSubtitleText(),
                                    words = words.distinctBy { it.timestampMs to it.text }
                                )
                            )
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            partialResults?.bestRecognitionText()?.let { text ->
                                if (segments.lastOrNull() != text) {
                                    segments += text
                                }
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) = Unit

                        override fun onSegmentResults(segmentResults: Bundle) {
                            segmentResults.bestRecognitionText()?.let { text ->
                                if (segments.lastOrNull() != text) {
                                    segments += text
                                }
                            }
                            words += segmentResults.recognitionWords()
                        }

                        override fun onEndOfSegmentedSession() {
                            finish(
                                RecognizedSpeech(
                                    text = segments.joinToString(" ").cleanSubtitleText(),
                                    words = words.distinctBy { it.timestampMs to it.text }
                                )
                            )
                        }
                    })

                        startListening(audioSource.toRecognitionIntent(sourceLanguageCode))
                    }
                }.onFailure { error ->
                    finish(
                        RecognizedSpeech(
                            errorReason = error.localizedMessage
                                ?.takeIf { it.isNotBlank() }
                                ?: "Khong khoi dong duoc dich vu nhan dang giong noi."
                        )
                    )
                }
            }

            continuation.invokeOnCancellation {
                mainHandler.post {
                    recognizer?.cancel()
                    recognizer?.destroy()
                    audioSource.close()
                }
            }
        }
    }
}

private data class PcmAudioSource(
    val readFd: ParcelFileDescriptor,
    val writeFd: ParcelFileDescriptor,
    val sampleRate: Int,
    val channelCount: Int
) {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun toRecognitionIntent(sourceLanguageCode: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, channelCount)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
            .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            .apply {
                if (sourceLanguageCode.isNotBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sourceLanguageCode)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                        RecognizerIntent.LANGUAGE_SWITCH_BALANCED
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true)
                    putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
                }
            }
    }

    fun close() {
        runCatching { readFd.close() }
        runCatching { writeFd.close() }
    }
}

private object VideoPcmAudioPipe {
    fun open(videoFile: File): PcmAudioSource {
        val extractor = MediaExtractor()
        extractor.setDataSource(videoFile.absolutePath)
        val trackIndex = findAudioTrack(extractor)
        if (trackIndex < 0) {
            extractor.release()
            throw IOException("No audio track")
        }

        val format = extractor.getTrackFormat(trackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pipe = ParcelFileDescriptor.createPipe()
        val source = PcmAudioSource(
            readFd = pipe[0],
            writeFd = pipe[1],
            sampleRate = sampleRate,
            channelCount = channelCount
        )

        Thread({
            decodeToPipe(
                extractor = extractor,
                trackIndex = trackIndex,
                format = format,
                writeFd = source.writeFd
            )
        }, "video-subtitle-audio-decode").apply {
            isDaemon = true
            start()
        }

        return source
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return -1
    }

    private fun decodeToPipe(
        extractor: MediaExtractor,
        trackIndex: Int,
        format: MediaFormat,
        writeFd: ParcelFileDescriptor
    ) {
        var codec: MediaCodec? = null
        val output = ParcelFileDescriptor.AutoCloseOutputStream(writeFd)
        try {
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val activeCodec = MediaCodec.createDecoderByType(mime)
            codec = activeCodec
            extractor.selectTrack(trackIndex)
            activeCodec.configure(format, null, null, 0)
            activeCodec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = activeCodec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = activeCodec.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            activeCodec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            activeCodec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = activeCodec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer = activeCodec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val bytes = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.get(bytes)
                        output.write(bytes)
                    }
                    outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    activeCodec.releaseOutputBuffer(outputIndex, false)
                }
            }
        } catch (_: Throwable) {
            // SpeechRecognizer will receive EOF and surface a user-facing unavailable state.
        } finally {
            runCatching { output.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}

private fun Bundle.bestRecognitionText(): String? {
    return getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
}

private fun speechRecognizerErrorReason(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Loi thu audio khi nhan dang giong noi."
        SpeechRecognizer.ERROR_CLIENT -> "Dich vu nhan dang giong noi khong chap nhan audio tu video nay."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thieu quyen nhan dang giong noi."
        SpeechRecognizer.ERROR_NETWORK -> "Loi mang khi nhan dang giong noi."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Nhan dang giong noi bi qua thoi gian mang."
        SpeechRecognizer.ERROR_NO_MATCH -> "Khong tim thay loi thoai phu hop trong video."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Dich vu nhan dang giong noi dang ban. Thu lai sau."
        SpeechRecognizer.ERROR_SERVER -> "Dich vu nhan dang giong noi bi loi."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Khong nghe thay loi thoai trong video."
        else -> "Nhan dang giong noi that bai (ma loi $error)."
    }
}

private fun Bundle.recognitionWords(): List<RecognizedWord> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return emptyList()
    return getParcelableArrayList(
        SpeechRecognizer.RECOGNITION_PARTS,
        RecognitionPart::class.java
    ).orEmpty()
        .mapNotNull { part ->
            val text = (part.formattedText ?: part.rawText).cleanSubtitleText()
            val timestamp = part.timestampMillis
            if (text.isBlank() || timestamp < 0L) {
                null
            } else {
                RecognizedWord(text = text, timestampMs = timestamp)
            }
        }
}

private fun File.durationMs(): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        retriever.release()
    }
}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}

private fun String.normalizedSpeechLanguageCode(): String {
    val clean = trim()
    if (clean.isBlank()) return ""
    return clean.replace('_', '-')
}

private fun String.baseLanguageCode(): String {
    return normalizedSpeechLanguageCode()
        .substringBefore("-")
        .lowercase()
        .takeIf { it.length in 2..3 }
        .orEmpty()
}

private fun String.cacheLanguageSuffix(): String {
    return normalizedSpeechLanguageCode()
        .ifBlank { "auto" }
        .lowercase()
}

private fun sourceTranscriptCacheKey(sourceLanguageCode: String): String {
    return "$ORIGINAL_AUDIO_TRANSCRIPT_CACHE_KEY:${sourceLanguageCode.cacheLanguageSuffix()}"
}

private const val ORIGINAL_AUDIO_TRANSCRIPT_CACHE_KEY = "source"
private const val SOURCE_TRANSCRIPT_PROVIDER_VERSION = "android-speech-source-v3"
private const val VIETNAMESE_LANGUAGE_CODE = "vi"

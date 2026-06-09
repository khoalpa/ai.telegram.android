package ai.telegram.android.work

import ai.telegram.android.data.MessageRepository
import ai.telegram.android.data.MediaCacheRepository
import ai.telegram.android.data.TranslationCacheRepository
import ai.telegram.android.data.TranslationJobRepository
import ai.telegram.android.data.VideoSubtitleCacheRepository
import ai.telegram.android.data.local.AppDatabase
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class TranslationPrefetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database = AppDatabase.get(applicationContext)
        val messageRepository = MessageRepository(database)
        val translationJobRepository = TranslationJobRepository(database)
        val messages = messageRepository.recentMessagesNeedingTranslation(PREFETCH_LIMIT)
        messages.forEach { message ->
            translationJobRepository.enqueue(
                message = message,
                targetLanguage = message.translationTargetLanguage.ifBlank { DEFAULT_TARGET_LANGUAGE }
            )
        }
        if (messages.isNotEmpty()) {
            TranslationWorkScheduler.schedule(applicationContext)
        }
        return Result.success()
    }

    private companion object {
        const val PREFETCH_LIMIT = 100
        const val DEFAULT_TARGET_LANGUAGE = "vi"
    }
}

class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database = AppDatabase.get(applicationContext)
        val translationCacheRepository = TranslationCacheRepository(database)
        val mediaCacheRepository = MediaCacheRepository(database)
        val videoSubtitleCacheRepository = VideoSubtitleCacheRepository(database)
        val translationJobRepository = TranslationJobRepository(database)
        val now = System.currentTimeMillis()

        translationCacheRepository.cleanup(
            cutoffMillis = now - TRANSLATION_CACHE_TTL_MILLIS,
            maxEntries = MAX_TRANSLATION_CACHE_ENTRIES
        )
        translationJobRepository.cleanupStaleFailed(
            cutoffMillis = now - FAILED_JOB_TTL_MILLIS
        )
        videoSubtitleCacheRepository.cleanup(
            cutoffMillis = now - VIDEO_SUBTITLE_CACHE_TTL_MILLIS,
            maxEntries = MAX_VIDEO_SUBTITLE_CACHE_ENTRIES
        )
        mediaCacheRepository.cleanup(
            cutoffMillis = now - MEDIA_CACHE_TTL_MILLIS,
            maxEntries = MAX_MEDIA_CACHE_ENTRIES,
            maxBytes = MAX_MEDIA_CACHE_BYTES
        )

        return Result.success()
    }

    private companion object {
        const val MAX_TRANSLATION_CACHE_ENTRIES = 20_000
        const val MAX_MEDIA_CACHE_ENTRIES = 5_000
        const val MAX_MEDIA_CACHE_BYTES = 512L * 1024L * 1024L
        const val MAX_VIDEO_SUBTITLE_CACHE_ENTRIES = 1_000
        const val TRANSLATION_CACHE_TTL_MILLIS = 90L * 24L * 60L * 60L * 1000L
        const val MEDIA_CACHE_TTL_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val VIDEO_SUBTITLE_CACHE_TTL_MILLIS = 90L * 24L * 60L * 60L * 1000L
        const val FAILED_JOB_TTL_MILLIS = 14L * 24L * 60L * 60L * 1000L
    }
}

object MaintenanceWorkScheduler {
    private const val PREFETCH_WORK_NAME = "translation_prefetch"
    private const val CACHE_CLEANUP_WORK_NAME = "cache_cleanup"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        val prefetchRequest = PeriodicWorkRequestBuilder<TranslationPrefetchWorker>(
            6,
            TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        ).build()

        val cleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            1,
            TimeUnit.DAYS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            PREFETCH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            prefetchRequest
        )
        workManager.enqueueUniquePeriodicWork(
            CACHE_CLEANUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }
}

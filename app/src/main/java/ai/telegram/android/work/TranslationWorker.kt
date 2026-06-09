package ai.telegram.android.work

import ai.telegram.android.data.BlacklistRepository
import ai.telegram.android.data.ChatRepository
import ai.telegram.android.data.MessageRepository
import ai.telegram.android.data.TranslationCacheRepository
import ai.telegram.android.data.TranslationJobRepository
import ai.telegram.android.data.TranslationJobStatus
import ai.telegram.android.data.local.AppDatabase
import ai.telegram.android.data.translation.MlKitOnDeviceTranslationProvider
import ai.telegram.android.data.translation.TranslationProcessor
import ai.telegram.android.R
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class TranslationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val database = AppDatabase.get(applicationContext)
        val messageRepository = MessageRepository(database)
        val chatRepository = ChatRepository(database)
        val blacklistRepository = BlacklistRepository(database)
        val translationCacheRepository = TranslationCacheRepository(database)
        val translationJobRepository = TranslationJobRepository(database)
        val translationProvider = MlKitOnDeviceTranslationProvider(
            context = applicationContext,
            reuseTranslators = true
        )
        val processor = TranslationProcessor(
            messageRepository = messageRepository,
            chatRepository = chatRepository,
            blacklistRepository = blacklistRepository,
            translationCacheRepository = translationCacheRepository,
            translationProvider = translationProvider,
            hiddenPreview = applicationContext.getString(R.string.translation_hidden),
            failedPreview = applicationContext.getString(R.string.translation_failed)
        )
        try {
            translationJobRepository.recoverStaleRunning(
                cutoffMillis = System.currentTimeMillis() - RUNNING_JOB_TIMEOUT_MILLIS
            )

            repeat(MAX_BATCH_ROUNDS) {
                val jobs = translationJobRepository.pending(BATCH_SIZE)
                if (jobs.isEmpty()) return Result.success()

                jobs.forEach { job ->
                    translationJobRepository.markRunning(job.messageUid)
                    val message = messageRepository.find(job.messageUid)
                    if (message == null) {
                        translationJobRepository.complete(job.messageUid)
                        return@forEach
                    }

                    val result = processor.process(message, job.targetLanguage)
                    if (result.completed) {
                        translationJobRepository.complete(job.messageUid)
                    } else {
                        when (TranslationRetryPolicy.nextFailedStatus(job.attempts)) {
                            TranslationJobStatus.Failed -> translationJobRepository.markFailed(job.messageUid)
                            TranslationJobStatus.Pending -> translationJobRepository.markPending(job.messageUid)
                            TranslationJobStatus.Running -> Unit
                        }
                    }
                }
            }

            return if (TranslationRetryPolicy.shouldRetry(translationJobRepository.pendingCount())) {
                Result.retry()
            } else {
                Result.success()
            }
        } finally {
            translationProvider.close()
        }
    }

    private companion object {
        const val BATCH_SIZE = 25
        const val MAX_BATCH_ROUNDS = 4
        const val RUNNING_JOB_TIMEOUT_MILLIS = 15L * 60L * 1000L
    }
}

object TranslationRetryPolicy {
    private const val MAX_ATTEMPTS = 3

    fun nextFailedStatus(attemptsBeforeCurrentRun: Int): TranslationJobStatus {
        return if (attemptsBeforeCurrentRun >= MAX_ATTEMPTS - 1) {
            TranslationJobStatus.Failed
        } else {
            TranslationJobStatus.Pending
        }
    }

    fun shouldRetry(pendingCount: Int): Boolean = pendingCount > 0
}

object TranslationWorkScheduler {
    private const val UNIQUE_WORK_NAME = "translation_queue_fast_v2"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<TranslationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

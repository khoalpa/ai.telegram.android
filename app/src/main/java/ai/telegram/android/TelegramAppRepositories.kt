package ai.telegram.android

import ai.telegram.android.data.BlacklistRepository
import ai.telegram.android.data.ChatHistoryStateRepository
import ai.telegram.android.data.ChatRepository
import ai.telegram.android.data.MediaCacheRepository
import ai.telegram.android.data.MessageRepository
import ai.telegram.android.data.SenderRepository
import ai.telegram.android.data.TranslationCacheRepository
import ai.telegram.android.data.TranslationJobRepository
import ai.telegram.android.data.VideoSubtitleCacheRepository
import ai.telegram.android.data.local.AppDatabase
import android.content.Context

class TelegramAppRepositories(
    database: AppDatabase
) {
    val chatRepository = ChatRepository(database)
    val chatHistoryStateRepository = ChatHistoryStateRepository(database)
    val senderRepository = SenderRepository(database)
    val messageRepository = MessageRepository(database)
    val blacklistRepository = BlacklistRepository(database)
    val translationCacheRepository = TranslationCacheRepository(database)
    val translationJobRepository = TranslationJobRepository(database)
    val mediaCacheRepository = MediaCacheRepository(database)
    val videoSubtitleCacheRepository = VideoSubtitleCacheRepository(database)

    companion object {
        fun from(context: Context): TelegramAppRepositories {
            return TelegramAppRepositories(AppDatabase.get(context.applicationContext))
        }
    }
}

package ai.telegram.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatEntity::class,
        SenderEntity::class,
        MessageEntity::class,
        ChatHistoryStateEntity::class,
        HiddenContentEntity::class,
        TranslationCacheEntity::class,
        TranslationJobEntity::class,
        MediaCacheEntity::class,
        VideoSubtitleCacheEntity::class
    ],
    version = 14,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun senderDao(): SenderDao
    abstract fun messageDao(): MessageDao
    abstract fun chatHistoryStateDao(): ChatHistoryStateDao
    abstract fun hiddenContentDao(): HiddenContentDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun translationJobDao(): TranslationJobDao
    abstract fun mediaCacheDao(): MediaCacheDao
    abstract fun videoSubtitleCacheDao(): VideoSubtitleCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chats (
                        id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        unreadCount INTEGER NOT NULL,
                        lastMessagePreview TEXT NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE messages_new (
                        uid TEXT NOT NULL PRIMARY KEY,
                        id INTEGER NOT NULL,
                        chatId INTEGER NOT NULL,
                        chatTitle TEXT NOT NULL,
                        author TEXT NOT NULL,
                        originalText TEXT NOT NULL,
                        translatedText TEXT NOT NULL,
                        detectedLanguage TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        mediaSizeMb INTEGER NOT NULL,
                        timestamp TEXT NOT NULL,
                        contentHash TEXT NOT NULL,
                        receivedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO messages_new (
                        uid, id, chatId, chatTitle, author, originalText, translatedText,
                        detectedLanguage, kind, mediaSizeMb, timestamp, contentHash, receivedAtMillis
                    )
                    SELECT '0:' || id, id, 0, chatTitle, author, originalText, translatedText,
                        detectedLanguage, kind, mediaSizeMb, timestamp, contentHash, receivedAtMillis
                    FROM messages
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE messages")
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO chats (
                        id, title, type, unreadCount, lastMessagePreview, updatedAtMillis
                    )
                    VALUES (0, 'Tin mẫu', 'demo', 0, 'Dữ liệu mẫu trước khi nối TDLib', strftime('%s','now') * 1000)
                    """.trimIndent()
                )
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN translationStatus TEXT NOT NULL DEFAULT 'Ready'")
            }
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS senders (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        type TEXT NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE messages ADD COLUMN senderId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS translation_jobs (
                        messageUid TEXT NOT NULL PRIMARY KEY,
                        targetLanguage TEXT NOT NULL,
                        status TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaFileId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaLocalPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaMimeType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaFileName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaThumbnailFileId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaThumbnailLocalPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaDownloadedPrefixBytes INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE translation_cache ADD COLUMN sourceLanguage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE translation_cache ADD COLUMN failureReason TEXT NOT NULL DEFAULT 'None'")
                db.execSQL("ALTER TABLE translation_cache ADD COLUMN failureMessage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE translation_cache ADD COLUMN expiresAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_cache (
                        fileId INTEGER NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        sizeMb INTEGER NOT NULL,
                        downloadedPrefixBytes INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        requestedAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        lastAccessedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS video_subtitle_cache (
                        fileId INTEGER NOT NULL,
                        targetLanguage TEXT NOT NULL,
                        providerVersion TEXT NOT NULL,
                        videoPath TEXT NOT NULL,
                        videoSizeBytes INTEGER NOT NULL,
                        videoModifiedAtMillis INTEGER NOT NULL,
                        sourceText TEXT NOT NULL,
                        cuesJson TEXT NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        lastAccessedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(fileId, targetLanguage, providerVersion)
                    )
                    """.trimIndent()
                )
            }
        }

        private val migration8To9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN translationFailureReason TEXT NOT NULL DEFAULT 'None'")
                db.execSQL("ALTER TABLE messages ADD COLUMN translationTargetLanguage TEXT NOT NULL DEFAULT 'vi'")
                db.execSQL("ALTER TABLE media_cache ADD COLUMN actualSizeBytes INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val migration9To10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN activeAction TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN lastReadInboxMessageId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN lastReadOutboxMessageId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN pinnedMessageId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN syncState TEXT NOT NULL DEFAULT 'Synced'")
                db.execSQL("ALTER TABLE messages ADD COLUMN isOutgoing INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val migration10To11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createPerformanceIndexes(db)
            }
        }

        private val migration11To12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_history_state (
                        chatId INTEGER NOT NULL PRIMARY KEY,
                        newestMessageId INTEGER NOT NULL,
                        oldestMessageId INTEGER NOT NULL,
                        lastSyncedAtMillis INTEGER NOT NULL,
                        olderHistoryExhausted INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_history_state_lastSyncedAtMillis ON chat_history_state(lastSyncedAtMillis)")
            }
        }

        private val migration12To13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE video_subtitle_cache ADD COLUMN sourceLanguageCode TEXT NOT NULL DEFAULT ''")
            }
        }

        private val migration13To14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN isMainList INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE senders ADD COLUMN isContact INTEGER NOT NULL DEFAULT 0")
            }
        }

        val migrations: Array<Migration>
            get() = arrayOf(
                migration1To2,
                migration2To3,
                migration3To4,
                migration4To5,
                migration5To6,
                migration6To7,
                migration7To8,
                migration8To9,
                migration9To10,
                migration10To11,
                migration11To12,
                migration12To13,
                migration13To14
            )

        private fun createPerformanceIndexes(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_updatedAtMillis ON chats(updatedAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_type ON chats(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_senders_displayName ON senders(displayName)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId_receivedAtMillis ON messages(chatId, receivedAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId_id ON messages(chatId, id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_senderId ON messages(senderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_translation_work ON messages(translationStatus, translationFailureReason, receivedAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_mediaFileId ON messages(mediaFileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_mediaThumbnailFileId ON messages(mediaThumbnailFileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_jobs_status_createdAtMillis ON translation_jobs(status, createdAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_jobs_status_updatedAtMillis ON translation_jobs(status, updatedAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_cache_state_updatedAtMillis ON media_cache(state, updatedAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_cache_lastAccessedAtMillis ON media_cache(lastAccessedAtMillis)")
        }

        private val cleanupCallback = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL(
                    """
                    UPDATE chats
                    SET lastMessagePreview = substr(lastMessagePreview, 1, 500)
                    WHERE length(lastMessagePreview) > 500
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_telegram_android.db"
                ).addMigrations(*migrations)
                    .addCallback(cleanupCallback)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

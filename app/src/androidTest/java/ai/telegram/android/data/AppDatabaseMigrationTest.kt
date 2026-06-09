package ai.telegram.android.data

import ai.telegram.android.DeviceSafeConnectedTest
import ai.telegram.android.data.local.AppDatabase
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DeviceSafeConnectedTest
class AppDatabaseMigrationTest {
    @Test
    fun migratesFromVersion1To13AndPreservesMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).apply {
            createVersion1Tables()
            execSQL(
                """
                INSERT INTO messages (
                    id, chatTitle, author, originalText, translatedText, detectedLanguage,
                    kind, mediaSizeMb, timestamp, contentHash, receivedAtMillis
                ) VALUES (
                    10, 'Legacy chat', 'Alice', 'Hello', 'Xin chao', 'en',
                    'Text', 0, '10:00', 'hash-hello', 1000
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO translation_cache (
                    contentHash, targetLanguage, providerVersion, translatedText, updatedAtMillis, hitCount
                ) VALUES (
                    'hash-hello', 'vi', 'legacy', 'Xin chao', 1000, 1
                )
                """.trimIndent()
            )
            version = 1
            close()
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*AppDatabase.migrations)
            .build()
        val db = database.openHelper.readableDatabase

        db.query(
            """
            SELECT uid, chatId, translationStatus, mediaThumbnailFileId, translationFailureReason,
                translationTargetLanguage, syncState, isOutgoing, isRead, isEdited, isPinned
            FROM messages WHERE uid = '0:10'
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("0:10", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
            assertEquals("Ready", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals("None", cursor.getString(4))
            assertEquals("vi", cursor.getString(5))
            assertEquals("Synced", cursor.getString(6))
            assertEquals(0, cursor.getInt(7))
            assertEquals(0, cursor.getInt(8))
            assertEquals(0, cursor.getInt(9))
            assertEquals(0, cursor.getInt(10))
        }
        db.query(
            """
            SELECT activeAction, lastReadInboxMessageId, lastReadOutboxMessageId, pinnedMessageId
            FROM chats WHERE id = 0
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
            assertEquals(0L, cursor.getLong(2))
            assertEquals(0L, cursor.getLong(3))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='translation_jobs'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='media_cache'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
        }
        db.query("PRAGMA table_info(media_cache)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(1)
            }
            assertEquals(true, "actualSizeBytes" in columns)
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='video_subtitle_cache'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
        }
        db.query("PRAGMA table_info(video_subtitle_cache)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(1)
            }
            assertEquals(true, "sourceLanguageCode" in columns)
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='chat_history_state'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_chat_history_state_lastSyncedAtMillis'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
        }
        db.query("SELECT failureReason, expiresAtMillis FROM translation_cache LIMIT 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("None", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_messages_chatId_receivedAtMillis'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_translation_jobs_status_createdAtMillis'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
        }
        database.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun SQLiteDatabase.createVersion1Tables() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER NOT NULL PRIMARY KEY,
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
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS hidden_content (
                contentHash TEXT NOT NULL PRIMARY KEY,
                preview TEXT NOT NULL,
                hiddenCount INTEGER NOT NULL,
                createdAt TEXT NOT NULL,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS translation_cache (
                contentHash TEXT NOT NULL,
                targetLanguage TEXT NOT NULL,
                providerVersion TEXT NOT NULL,
                translatedText TEXT NOT NULL,
                updatedAtMillis INTEGER NOT NULL,
                hitCount INTEGER NOT NULL,
                PRIMARY KEY(contentHash, targetLanguage, providerVersion)
            )
            """.trimIndent()
        )
    }

    private companion object {
        const val TEST_DB = "migration-1-13-test"
    }
}

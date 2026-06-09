package ai.telegram.android.data.telegram

import ai.telegram.android.BuildConfig
import android.content.Context

data class TdLibConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDirectory: String,
    val filesDirectory: String,
    val databaseEncryptionKey: ByteArray,
    val systemLanguageCode: String = "vi",
    val deviceModel: String = "Android",
    val systemVersion: String = android.os.Build.VERSION.RELEASE ?: "Android",
    val applicationVersion: String = BuildConfig.VERSION_NAME,
    val useTestDc: Boolean = false
) {
    val isConfigured: Boolean = apiId > 0 && apiHash.isNotBlank()

    companion object {
        private const val DATABASE_DIR_NAME = "tdlib-db"
        private const val FILES_DIR_NAME = "tdlib-files"

        fun from(context: Context): TdLibConfig {
            val appContext = context.applicationContext
            return TdLibConfig(
                apiId = BuildConfig.TELEGRAM_API_ID,
                apiHash = BuildConfig.TELEGRAM_API_HASH,
                databaseDirectory = appContext.getDir(DATABASE_DIR_NAME, Context.MODE_PRIVATE).absolutePath,
                filesDirectory = appContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE).absolutePath,
                databaseEncryptionKey = TdLibDatabaseKeyStore.getOrCreate(appContext)
            )
        }

        fun resetLocalData(context: Context) {
            val appContext = context.applicationContext
            TdLibDatabaseKeyStore.reset(appContext)
            appContext.getDir(DATABASE_DIR_NAME, Context.MODE_PRIVATE).deleteRecursively()
            appContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE).deleteRecursively()
        }
    }
}

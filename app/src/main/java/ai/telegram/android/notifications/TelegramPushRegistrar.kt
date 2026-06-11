package ai.telegram.android.notifications

import ai.telegram.android.data.telegram.TelegramClient
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Proxy
import kotlin.coroutines.resume

class TelegramPushRegistrar(
    context: Context
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun registerIfAvailable(client: TelegramClient): PushRegistrationResult {
        val token = firebaseMessagingTokenOrNull()
            ?: return PushRegistrationResult.FcmUnavailable
        if (token == prefs.getString(PREF_LAST_TOKEN, "")) {
            return PushRegistrationResult.AlreadyRegistered
        }
        client.registerDeviceForPush(token)
        prefs.edit { putString(PREF_LAST_TOKEN, token) }
        return PushRegistrationResult.Registered
    }

    private suspend fun firebaseMessagingTokenOrNull(): String? {
        return withTimeoutOrNull(TOKEN_TIMEOUT_MILLIS) {
            runCatching {
                val firebaseMessagingClass = Class.forName("com.google.firebase.messaging.FirebaseMessaging")
                val messaging = firebaseMessagingClass.getMethod("getInstance").invoke(null)
                val task = firebaseMessagingClass.getMethod("getToken").invoke(messaging)
                val taskClass = Class.forName("com.google.android.gms.tasks.Task")
                val listenerClass = Class.forName("com.google.android.gms.tasks.OnCompleteListener")
                suspendCancellableCoroutine<String?> { continuation ->
                    val listener = Proxy.newProxyInstance(
                        listenerClass.classLoader,
                        arrayOf(listenerClass)
                    ) { _, method, args ->
                        if (method.name == "onComplete") {
                            val completedTask = args?.firstOrNull()
                            val token = completedTask?.firebaseTokenResultOrNull()
                            if (continuation.isActive) continuation.resume(token)
                        }
                        null
                    }
                    taskClass.getMethod("addOnCompleteListener", listenerClass)
                        .invoke(task, listener)
                }
            }.getOrNull()
        }
    }

    private fun Any.firebaseTokenResultOrNull(): String? {
        val successful = javaClass.getMethod("isSuccessful").invoke(this) as? Boolean ?: false
        if (!successful) return null
        return javaClass.getMethod("getResult").invoke(this) as? String
    }

    companion object {
        private const val PREFS_NAME = "ai_telegram_push"
        private const val PREF_LAST_TOKEN = "last_registered_token"
        private const val TOKEN_TIMEOUT_MILLIS = 8_000L
    }
}

enum class PushRegistrationResult {
    Registered,
    AlreadyRegistered,
    FcmUnavailable
}

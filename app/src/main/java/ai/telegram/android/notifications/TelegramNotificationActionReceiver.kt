package ai.telegram.android.notifications

import ai.telegram.android.data.telegram.TdLibConfig
import ai.telegram.android.data.telegram.TdLibReflectionClient
import ai.telegram.android.data.telegram.TdLibStatus
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TelegramNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleAction(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(TelegramNotificationManager.EXTRA_ACTION_CHAT_ID, 0L)
        if (chatId == 0L) return

        val client = backgroundClient(context)
        client.start()
        if (!client.awaitReady()) {
            client.close()
            return
        }

        when (intent.action) {
            TelegramNotificationManager.ACTION_REPLY -> {
                val replyText = NotificationActionPolicy.sanitizeReplyText(
                    RemoteInput.getResultsFromIntent(intent)
                        ?.getCharSequence(TelegramNotificationManager.KEY_TEXT_REPLY)
                )
                if (replyText.isNotBlank()) {
                    client.sendTextMessage(chatId, replyText)
                    client.markChatRead(chatId)
                    TelegramNotificationManager(context).cancelChat(chatId)
                }
            }
            TelegramNotificationManager.ACTION_MARK_READ -> {
                client.markChatRead(chatId)
                TelegramNotificationManager(context).cancelChat(chatId)
            }
        }
        Thread.sleep(SEND_FLUSH_DELAY_MILLIS)
        client.close()
    }

    private fun backgroundClient(context: Context): ReadyAwaitingClient {
        val readyLatch = CountDownLatch(1)
        val client = TdLibReflectionClient(
            config = TdLibConfig.from(context),
            onStatus = { status ->
                if (status == TdLibStatus.Ready) readyLatch.countDown()
            },
            onChat = {},
            onSender = {},
            onMessage = {},
            onOperationError = {}
        )
        return ReadyAwaitingClient(client, readyLatch)
    }

    private class ReadyAwaitingClient(
        private val delegate: TdLibReflectionClient,
        private val readyLatch: CountDownLatch
    ) : ai.telegram.android.data.telegram.TelegramClient by delegate {
        fun awaitReady(): Boolean {
            return readyLatch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val READY_TIMEOUT_SECONDS = 8L
        const val SEND_FLUSH_DELAY_MILLIS = 500L
    }
}

package ai.telegram.android.debug

import ai.telegram.android.MainActivity
import ai.telegram.android.R
import ai.telegram.android.notifications.TelegramNotificationManager
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class DebugNotificationQaReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_POST_CHAT_NOTIFICATION) return
        if (!canPostNotifications(context)) return

        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L).takeIf { it != 0L } ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "AI Telegram QA" }
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Tap to open QA chat" }
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(TelegramNotificationManager.EXTRA_OPEN_CHAT_ID, chatId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ai_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI Telegram QA",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Debug-only QA notification tap route"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_POST_CHAT_NOTIFICATION = "ai.telegram.android.DEBUG_POST_CHAT_NOTIFICATION"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"

        private const val CHANNEL_ID = "debug_qa_notifications"
        private const val NOTIFICATION_ID = 0x0D06A11
    }
}

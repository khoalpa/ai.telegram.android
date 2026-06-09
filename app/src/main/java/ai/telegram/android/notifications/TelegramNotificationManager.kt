package ai.telegram.android.notifications

import ai.telegram.android.MainActivity
import ai.telegram.android.R
import ai.telegram.android.data.MessageKind
import ai.telegram.android.data.TelegramMessage
import ai.telegram.android.data.telegram.TelegramCall
import ai.telegram.android.data.telegram.TelegramCallState
import ai.telegram.android.ui.MessagePrivacyPolicy
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class TelegramNotificationManager(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private var activeIncomingCallId: Int? = null
    private var incomingCallRingtone: Ringtone? = null

    fun ensureMessageChannel() {
        val channel = NotificationChannel(
            CHANNEL_MESSAGES,
            appContext.getString(R.string.notification_channel_messages_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.notification_channel_messages_description)
        }
        appContext
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun ensureCallChannel() {
        val channel = NotificationChannel(
            CHANNEL_CALLS,
            appContext.getString(R.string.notification_channel_calls_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.notification_channel_calls_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(null, null)
        }
        appContext
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean {
        return hasPostNotificationsPermission(appContext) &&
            notificationManager.areNotificationsEnabled()
    }

    fun notifyNewMessage(message: TelegramMessage) {
        ensureMessageChannel()
        if (!canPostNotifications()) return

        val title = message.chatTitle
            .ifBlank { message.author }
            .ifBlank { appContext.getString(R.string.notification_new_message) }
        val text = message.notificationText()
        val contentIntent = openChatIntent(message.chatId)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_ai_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_MESSAGES)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(notificationId(message.chatId), notification)
        notifyMessageGroupSummary()
    }

    fun notifyIncomingCall(call: TelegramCall, callerName: String?) {
        if (call.isOutgoing || call.state != TelegramCallState.Pending) {
            cancelIncomingCall(call.id)
            return
        }
        ensureCallChannel()
        if (!canPostNotifications()) return
        startIncomingCallAlert(call.id)

        val title = callerName
            ?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.notification_incoming_call)
        val text = appContext.getString(
            if (call.isVideo) {
                R.string.notification_incoming_video_call
            } else {
                R.string.notification_incoming_voice_call
            }
        )
        val contentIntent = openCallsIntent(call.id)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_CALLS)
            .setSmallIcon(if (call.isVideo) R.drawable.ic_ai_video else R.drawable.ic_ai_call)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(incomingCallNotificationId(call.id), notification)
    }

    fun cancelIncomingCall(callId: Int) {
        if (activeIncomingCallId == callId) {
            stopIncomingCallAlert()
        }
        notificationManager.cancel(incomingCallNotificationId(callId))
    }

    fun cancelChat(chatId: Long) {
        notificationManager.cancel(notificationId(chatId))
    }

    private fun openChatIntent(chatId: Long): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            notificationId(chatId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            MESSAGE_GROUP_SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyMessageGroupSummary() {
        if (!canPostNotifications()) return
        val summary = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_ai_chat)
            .setContentTitle(appContext.getString(R.string.notification_messages_summary_title))
            .setContentText(appContext.getString(R.string.notification_messages_summary_text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_MESSAGES)
            .setGroupSummary(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(MESSAGE_GROUP_SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun openCallsIntent(callId: Int): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CALLS, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            incomingCallNotificationId(callId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startIncomingCallAlert(callId: Int) {
        if (activeIncomingCallId == callId && incomingCallRingtone?.isPlaying == true) return
        stopIncomingCallAlert()
        activeIncomingCallId = callId
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        incomingCallRingtone = RingtoneManager.getRingtone(appContext, ringtoneUri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
        vibrateIncomingCall()
    }

    private fun stopIncomingCallAlert() {
        incomingCallRingtone?.stop()
        incomingCallRingtone = null
        activeIncomingCallId = null
    }

    private fun vibrateIncomingCall() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400, 600), -1)
        )
    }

    private fun TelegramMessage.notificationText(): String {
        val readableText = MessagePrivacyPolicy.readableText(this)
        if (readableText.isNotBlank()) return readableText
        if (originalText.isNotBlank()) return appContext.getString(R.string.translation_hidden)
        return when (kind) {
            MessageKind.Image -> appContext.getString(R.string.media_image)
            MessageKind.Video -> appContext.getString(R.string.media_video)
            MessageKind.File -> appContext.getString(R.string.media_file)
            MessageKind.Text -> appContext.getString(R.string.no_message_preview)
        }
    }

    companion object {
        const val EXTRA_OPEN_CHAT_ID = "ai.telegram.android.extra.OPEN_CHAT_ID"
        const val EXTRA_OPEN_CALLS = "ai.telegram.android.extra.OPEN_CALLS"

        private const val CHANNEL_MESSAGES = "telegram_messages"
        private const val CHANNEL_CALLS = "telegram_calls"
        private const val GROUP_MESSAGES = "telegram_messages"
        private const val MESSAGE_GROUP_SUMMARY_NOTIFICATION_ID = 0x061A1000

        fun hasPostNotificationsPermission(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        }

        private fun notificationId(chatId: Long): Int {
            return chatId.hashCode()
        }

        private fun incomingCallNotificationId(callId: Int): Int {
            return 0x0A11C000 or (callId and 0x0000FFFF)
        }
    }
}

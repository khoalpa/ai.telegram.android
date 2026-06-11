package ai.telegram.android.notifications

object NotificationActionPolicy {
    const val MaxReplyTextChars = 4096

    fun sanitizeReplyText(raw: CharSequence?): String {
        return raw
            ?.toString()
            .orEmpty()
            .trim()
            .take(MaxReplyTextChars)
    }
}

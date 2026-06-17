package ai.telegram.android

import ai.telegram.android.data.MessageKind
import android.net.Uri
import java.util.UUID

internal enum class ChatFolderFilter(val labelRes: Int) {
    All(R.string.chat_filter_all),
    Unread(R.string.chat_filter_unread),
    Private(R.string.chat_filter_private),
    Secret(R.string.chat_filter_secret),
    Groups(R.string.chat_filter_groups),
    Channels(R.string.chat_filter_channels)
}

internal enum class ChatContentFilter(val labelRes: Int) {
    All(R.string.content_filter_all),
    Media(R.string.content_filter_media),
    PhotosVideos(R.string.content_filter_photos_videos),
    Audio(R.string.content_filter_audio),
    Voice(R.string.content_filter_voice),
    Stickers(R.string.content_filter_stickers),
    Files(R.string.content_filter_files),
    Links(R.string.content_filter_links)
}

internal enum class ChatDateFilter(val labelRes: Int) {
    All(R.string.date_filter_all),
    Today(R.string.date_filter_today),
    Last7Days(R.string.date_filter_7d),
    Last30Days(R.string.date_filter_30d)
}

internal enum class SharedGalleryFilter(val labelRes: Int) {
    Media(R.string.content_filter_media),
    PhotosVideos(R.string.content_filter_photos_videos),
    Audio(R.string.content_filter_audio),
    Voice(R.string.content_filter_voice),
    Stickers(R.string.content_filter_stickers),
    Files(R.string.content_filter_files),
    Links(R.string.content_filter_links)
}

internal enum class MessageListOverlayState {
    None,
    Loading,
    Empty
}

internal enum class ComposerAttachmentType(val labelRes: Int, val iconRes: Int) {
    Photo(R.string.composer_attach_photo, android.R.drawable.ic_menu_gallery),
    Video(R.string.composer_attach_video, R.drawable.ic_ai_video),
    Voice(R.string.composer_attach_voice, android.R.drawable.ic_btn_speak_now),
    VideoMessage(R.string.composer_attach_video_message, R.drawable.ic_ai_video),
    File(R.string.composer_attach_file, R.drawable.ic_ai_paperclip),
    Album(R.string.composer_attach_album, android.R.drawable.ic_menu_upload),
    Camera(R.string.composer_attach_camera, android.R.drawable.ic_menu_camera)
}

internal data class ChatSenderFilterOption(
    val senderId: String,
    val label: String,
    val count: Int
)

data class PendingComposerMedia(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val kind: MessageKind,
    val displayName: String,
    val caption: String = "",
    val highQualityPhoto: Boolean = false
)

internal const val MIN_POLL_OPTIONS = 2
internal const val MAX_POLL_OPTIONS = 10
internal const val MAX_COMPOSER_MEDIA = 10
internal const val MAX_COMPOSER_CAPTION_LENGTH = 1024
internal const val BYTES_PER_KB = 1024L
internal const val BYTES_PER_MB = 1024L * 1024L
internal const val MIN_VIDEO_START_BYTES = 256L * 1024L
internal const val VIDEO_FAST_START_POLL_DELAY_MS = 350L
internal const val VIDEO_FAST_START_POLL_ATTEMPTS = 24
internal const val VIDEO_MIN_BUFFER_MS = 500
internal const val VIDEO_MAX_BUFFER_MS = 5_000
internal const val VIDEO_BUFFER_FOR_PLAYBACK_MS = 250
internal const val VIDEO_REVIEW_PREFS = "video_review"
internal const val VIDEO_REVIEW_POSITION_PREFIX = "position:"
internal const val VIDEO_REVIEW_BOOKMARKS_PREFIX = "bookmarks:"
internal const val VIDEO_REVIEW_NOTES_PREFIX = "notes:"
internal const val MESSAGE_LOADING_DEBOUNCE_MS = 280L
internal const val MESSAGE_EMPTY_RELOAD_GRACE_MS = 5_000L
internal const val MESSAGE_CONTENT_CROSSFADE_MS = 180

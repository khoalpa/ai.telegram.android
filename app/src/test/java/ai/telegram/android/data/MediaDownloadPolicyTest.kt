package ai.telegram.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadPolicyTest {
    private val policy = MediaDownloadPolicy()

    @Test
    fun roaming_neverAutoDownloadsMedia() {
        val image = policy.decide(NetworkMode.Roaming, MessageKind.Image, sizeMb = 1)
        val video = policy.decide(NetworkMode.Roaming, MessageKind.Video, sizeMb = 1)

        assertFalse(image.autoDownload)
        assertFalse(video.autoDownload)
    }

    @Test
    fun mobileData_onlyAutoDownloadsTinyImages() {
        val tinyImage = policy.decide(NetworkMode.MobileData, MessageKind.Image, sizeMb = 1)
        val largerImage = policy.decide(NetworkMode.MobileData, MessageKind.Image, sizeMb = 2)
        val file = policy.decide(NetworkMode.MobileData, MessageKind.File, sizeMb = 1)

        assertTrue(tinyImage.autoDownload)
        assertFalse(largerImage.autoDownload)
        assertFalse(file.autoDownload)
    }

    @Test
    fun wifi_autoDownloadsSmallImagesOnly() {
        val smallImage = policy.decide(NetworkMode.Wifi, MessageKind.Image, sizeMb = 8)
        val largeImage = policy.decide(NetworkMode.Wifi, MessageKind.Image, sizeMb = 9)
        val video = policy.decide(NetworkMode.Wifi, MessageKind.Video, sizeMb = 4)

        assertTrue(smallImage.autoDownload)
        assertFalse(largeImage.autoDownload)
        assertFalse(video.autoDownload)
    }
}

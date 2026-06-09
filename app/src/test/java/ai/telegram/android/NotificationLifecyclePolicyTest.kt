package ai.telegram.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLifecyclePolicyTest {
    @Test
    fun suppressesNotificationOnlyForVisibleSelectedChat() {
        assertFalse(
            shouldNotifyIncomingTelegramMessage(
                notificationsEnabled = true,
                notificationPermissionGranted = true,
                isOutgoing = false,
                messageChatId = 42L,
                selectedChatId = 42L,
                selectedTab = AppTab.Chats,
                isAppForeground = true
            )
        )
    }

    @Test
    fun notifiesSelectedChatWhenAppIsBackgrounded() {
        assertTrue(
            shouldNotifyIncomingTelegramMessage(
                notificationsEnabled = true,
                notificationPermissionGranted = true,
                isOutgoing = false,
                messageChatId = 42L,
                selectedChatId = 42L,
                selectedTab = AppTab.Chats,
                isAppForeground = false
            )
        )
    }

    @Test
    fun notifiesSelectedChatWhenDifferentTabIsVisible() {
        assertTrue(
            shouldNotifyIncomingTelegramMessage(
                notificationsEnabled = true,
                notificationPermissionGranted = true,
                isOutgoing = false,
                messageChatId = 42L,
                selectedChatId = 42L,
                selectedTab = AppTab.Settings,
                isAppForeground = true
            )
        )
    }

    @Test
    fun respectsNotificationSettingsPermissionAndOutgoingMessages() {
        assertFalse(
            shouldNotifyIncomingTelegramMessage(
                notificationsEnabled = false,
                notificationPermissionGranted = true,
                isOutgoing = false,
                messageChatId = 42L,
                selectedChatId = null,
                selectedTab = AppTab.Chats,
                isAppForeground = false
            )
        )
        assertFalse(
            shouldNotifyIncomingTelegramMessage(
                notificationsEnabled = true,
                notificationPermissionGranted = false,
                isOutgoing = false,
                messageChatId = 42L,
                selectedChatId = null,
                selectedTab = AppTab.Chats,
                isAppForeground = false
            )
        )
        assertFalse(
            shouldNotifyIncomingTelegramMessage(
                notificationsEnabled = true,
                notificationPermissionGranted = true,
                isOutgoing = true,
                messageChatId = 42L,
                selectedChatId = null,
                selectedTab = AppTab.Chats,
                isAppForeground = false
            )
        )
    }
}

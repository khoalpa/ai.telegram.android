package ai.telegram.android

import ai.telegram.android.data.telegram.TdLibStatus
import ai.telegram.android.ui.AiTelegramTheme
import ai.telegram.android.ui.AppThemeMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ComposeUiConnectedTest
class NavigationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerTabsAndSearch_emitExpectedNavigationCallbacks() {
        var selectedTab: AppTab? = null
        var searchOpened = false

        composeRule.setContent {
            AiTelegramTheme(themeMode = AppThemeMode.Light) {
                AiTelegramHeader(
                    tdLibStatus = TdLibStatus.Ready,
                    selectedTab = AppTab.Chats,
                    unreadCount = 0,
                    unreadChannelCount = 0,
                    contactCount = 0,
                    onMenuClick = {},
                    onCreateClick = {},
                    onSearchClick = { searchOpened = true },
                    onStatusClick = {},
                    onTabSelected = { selectedTab = it }
                )
            }
        }

        composeRule.onNodeWithTag("header_tab_Channels").performClick()
        composeRule.runOnIdle {
            assertEquals(AppTab.Channels, selectedTab)
        }

        composeRule.onNodeWithTag("header_tab_Contacts").performClick()
        composeRule.runOnIdle {
            assertEquals(AppTab.Contacts, selectedTab)
        }

        composeRule.onNodeWithTag("header_tab_Settings").performClick()
        composeRule.runOnIdle {
            assertEquals(AppTab.Settings, selectedTab)
        }

        composeRule.onNodeWithTag("header_search").performClick()
        composeRule.runOnIdle {
            assertTrue(searchOpened)
        }
    }

    @Test
    fun menuCacheItem_emitsCacheNavigationCallback() {
        var selectedTab: AppTab? = null

        composeRule.setContent {
            AiTelegramTheme(themeMode = AppThemeMode.Light) {
                MenuScreen(onSelect = { selectedTab = it })
            }
        }

        composeRule.onNodeWithTag("menu_list")
            .performScrollToNode(hasTestTag("menu_item_Cache"))
        composeRule.onNodeWithTag("menu_item_Cache").performClick()

        composeRule.runOnIdle {
            assertEquals(AppTab.Cache, selectedTab)
        }
    }
}

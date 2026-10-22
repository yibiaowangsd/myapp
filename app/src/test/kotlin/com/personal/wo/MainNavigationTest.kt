package com.personal.wo

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.personal.wo.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun exactlyTwoTabsCanBeSelectedAndRestored() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            WoTheme(dark=false) { AppNavigation(tab) { tab=it } }
        }
        compose.onAllNodes(isSelectable()).assertCountEquals(2)
        compose.onNodeWithText("首页").assertIsSelected()
        compose.onNodeWithText("成就").performClick().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("成就").assertIsSelected()
        compose.onNodeWithText("设置").assertDoesNotExist()
        compose.onNodeWithText("首页").performClick().assertIsSelected()
        listOf("回忆","连接","我的").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun settingsKeepsPermissionConnectionAndTrendActionsReachable() {
        val opened=mutableListOf<String>()
        compose.setContent {
            WoTheme(dark=false) { Surface {
                SettingsShortcuts({opened+="permissions"},{opened+="connections"},{opened+="insights"})
            } }
        }
        compose.onNodeWithText("记录来源").performClick()
        compose.onNodeWithText("应用与连接").performClick()
        compose.onNodeWithText("生活趋势").performClick()
        assertEquals(listOf("permissions","connections","insights"),opened)
    }
}

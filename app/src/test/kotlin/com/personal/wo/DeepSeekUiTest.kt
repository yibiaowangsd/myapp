package com.personal.wo

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.personal.wo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeepSeekUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun configuresDeepSeekWithoutAddingAConversation() {
        var saved:Triple<String,String,Boolean>?=null
        compose.setContent { WoTheme(dark=false) { Surface { DeepSeekSettings(DeepSeekUiState(loaded=true),false,
            {key,model,auto->saved=Triple(key,model,auto)},{},{},{},{}) } } }
        compose.onNodeWithTag("deepseek-key").performTextInput("synthetic-test-key")
        compose.onNodeWithTag("deepseek-automatic").performClick()
        compose.onNodeWithTag("deepseek-save").performScrollTo().performClick()
        assertEquals(Triple("synthetic-test-key","deepseek-flash",true),saved)
        compose.onNodeWithTag("ai-question").assertDoesNotExist()
    }
    @Test fun demoCannotSendRecordsToDeepSeek() {
        compose.setContent { WoTheme(dark=false) { Surface { DeepSeekSettings(DeepSeekUiState(loaded=true,hasKey=true),true,{_,_,_->},{},{},{},{}) } } }
        compose.onNodeWithTag("deepseek-save").performScrollTo().assertIsNotEnabled()
    }
}

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
class SharedDataUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun setupConfiguresADataSourceAndHasNoConversationOrOpenAiKey() {
        var saved:Pair<String,String>?=null
        compose.setContent { WoTheme(dark=false) { Surface { SharedDataSettings(SharedDataUiState(loaded=true),false,{repo,token->saved=repo to token},{},{}) } } }
        compose.onNodeWithTag("shared-repository").performTextInput("example/life-data")
        compose.onNodeWithTag("shared-token").performTextInput("test-token")
        compose.onNodeWithTag("shared-save").performClick()
        assertEquals("example/life-data" to "test-token",saved)
        compose.onNodeWithTag("ai-question").assertDoesNotExist()
    }
    @Test fun demoCannotUploadSyntheticRecords() {
        compose.setContent { WoTheme(dark=false) { Surface { SharedDataSettings(SharedDataUiState(loaded=true,repository="example/data",hasToken=true),true,{_,_->},{},{}) } } }
        compose.onNodeWithTag("shared-save").assertIsNotEnabled()
    }
}

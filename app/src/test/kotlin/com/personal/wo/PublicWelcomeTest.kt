package com.personal.wo

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.personal.wo.collector.PublicWelcomePlan
import com.personal.wo.ui.WelcomeScreen
import com.personal.wo.ui.WoTheme
import java.io.File
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
class PublicWelcomeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun existingAndPausedUsersNeverReenterFirstRunSetup() {
        assertTrue(PublicWelcomePlan.shouldShow(false,false,null))
        assertFalse(PublicWelcomePlan.shouldShow(true,false,null))
        assertFalse(PublicWelcomePlan.shouldShow(false,true,null))
        assertFalse(PublicWelcomePlan.shouldShow(false,false,123L))
    }

    @Test fun startingWithPhotosNeverRequestsLocationOrSpecialSources() {
        val requested=PublicWelcomePlan.photoPermissions(35).toSet()
        assertEquals(setOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),requested)
        assertEquals(setOf(Manifest.permission.READ_EXTERNAL_STORAGE),PublicWelcomePlan.photoPermissions(29).toSet())
    }

    @Test fun firstRunWaitsForAnExplicitChoiceAndAllowsSkipping() {
        var photos=0
        var skips=0
        compose.setContent { WoTheme { WelcomeScreen({photos++},{skips++}) } }
        compose.waitForIdle()
        assertEquals(0,photos)
        assertEquals(0,skips)
        val file=File("build/ui-previews/wo-2-welcome.png")
        file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        compose.onNodeWithTag("welcome-skip").performScrollTo().performClick()
        assertEquals(1,skips)
        assertEquals(0,photos)
        compose.onNodeWithTag("welcome-photos").performScrollTo().performClick()
        assertEquals(1,photos)
    }
}

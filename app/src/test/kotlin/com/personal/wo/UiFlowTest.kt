package com.personal.wo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import com.personal.wo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun state()=WoUiState(loaded=true,policy=Policy(requested=true),day=demoDay(LocalDate.of(2026,10,15)),demo=true)
    private fun capture(name:String) {
        compose.waitForIdle()
        val file=File("build/ui-previews/$name.png")
        file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun permissionsRequireAnExplicitConfirmationAndAllowSkipping() {
        var requests=0
        var started:Set<Source>?=null
        var browsed=false
        val current=mutableStateOf(WoUiState(loaded=true,sources=Source.entries.map{SourceState(it)}))
        compose.setContent { WoTheme(dark=false) { OnboardingFlow(current.value,{requests++},{started=it},{browsed=true},{}) } }
        compose.onNodeWithText("不用写日记，\n也能记住今天。").assertIsDisplayed()
        capture("01-welcome")
        assertEquals(0,requests)
        compose.onNodeWithText("设置记录权限").performClick()
        compose.onNodeWithTag("start-recording").assertIsNotEnabled()
        capture("02-permissions")
        compose.onAllNodesWithText("去授权")[0].performClick()
        assertEquals(0,requests)
        compose.onNodeWithTag("confirm-permission").performClick()
        assertEquals(1,requests)
        compose.onNodeWithTag("start-recording").assertIsNotEnabled()
        compose.runOnIdle { current.value=current.value.copy(sources=Source.entries.map{SourceState(it,authorization=if(it==Source.USAGE)"GRANTED" else "DENIED")}) }
        compose.onNodeWithTag("start-recording").assertIsEnabled().performClick()
        assertNotNull(started)
        assertTrue(Source.USAGE in started!!)
        compose.onNodeWithText("暂时跳过，先进入首页").performClick()
        assertTrue(browsed)
    }
    @Test fun dashboardShowsActualDataAndScrollableVisuals() {
        var selected:Episode?=null
        compose.setContent { WoTheme(dark=false) {
            Scaffold(bottomBar={AppNavigation(0){}}) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
                    TodayDashboard(state(),{},{},{selected=it},{},{},{})
                }
            }
        } }
        compose.onNodeWithText("手机使用").assertIsDisplayed()
        compose.onNodeWithText("4,280").assertExists()
        capture("03-today")
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("时间花在了哪里"))
        capture("04-visuals")
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("办公室"))
        compose.onNodeWithText("办公室",substring=false).performClick()
        assertEquals("办公室附近停留",selected?.title)
    }
    @Test fun emptyDashboardOffersSetupAndDoesNotInventMeasurements() {
        var setup=false
        val d=state().day!!.copy(episodes=emptyList(),metrics=emptyList(),sentences=emptyList())
        compose.setContent { WoTheme(dark=false) { TodayDashboard(WoUiState(loaded=true,day=d),{},{setup=true},{},{},{},{}) } }
        compose.onNodeWithText("选择记录内容").performClick()
        assertTrue(setup)
        compose.onNodeWithText("4,280").assertDoesNotExist()
        capture("05-empty")
    }
    @Test @Config(qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
    fun narrowScreenAndLargeTextKeepSetupAccessible() {
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.5f)) {
                WoTheme(dark=false) { OnboardingFlow(WoUiState(loaded=true),{},{},{},{}) }
            }
        }
        compose.onNodeWithText("设置记录权限").assertIsDisplayed().performClick()
        compose.onNodeWithText("暂时跳过，先进入首页").assertIsDisplayed()
        capture("06-large-text")
    }
    @Test fun darkDashboardRendersAndExitsDemo() {
        var exited=false
        compose.setContent { WoTheme(dark=true) { Surface(color=MaterialTheme.colorScheme.background) { TodayDashboard(state(),{},{},{},{},{exited=!it},{}) } } }
        capture("07-dark")
        compose.onNodeWithText("退出演示").performClick()
        assertTrue(exited)
    }
    @Test fun timelineFiltersAndOpensAnEpisode() {
        var selected:Episode?=null
        compose.setContent { WoTheme(dark=false) { TimelinePage(state(),{},{selected=it}) } }
        compose.onNodeWithText("应用",substring=false).performClick()
        compose.onNodeWithText("浏览器").performScrollTo().performClick()
        assertEquals("浏览器",selected?.title)
        capture("08-timeline")
    }
    @Test fun historyCalendarOpensSelectedDate() {
        var selected:LocalDate?=null
        compose.setContent { WoTheme(dark=false) { Surface(color=MaterialTheme.colorScheme.background) { HistoryPage(state().copy(history=listOf(state().day!!))){selected=it} } } }
        capture("09-history")
        compose.onNodeWithText("15",substring=false).performClick()
        assertEquals(15,selected?.dayOfMonth)
    }
    @Test fun mePageKeepsPermissionsAndPrivacyReachable() {
        var permissions=false
        var privacy=false
        compose.setContent { WoTheme(dark=false) { Surface(color=MaterialTheme.colorScheme.background) { MePage(state(),{permissions=true},{privacy=true},{},{},{}) } } }
        capture("10-me")
        compose.onNodeWithText("记录权限").performClick()
        compose.onNodeWithText("数据与隐私").performClick()
        assertTrue(permissions && privacy)
    }
}

package com.personal.wo

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayDrumUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun state():WoUiState {
        val base=demoDay(LocalDate.of(2026,10,15),"Asia/Shanghai")
        val entries=listOf(
            Episode("morning",startMs=base.windowStartMs+9*3_600_000L,endMs=base.windowStartMs+10*3_600_000L,lane=Lane.PLACE,kind="STAY",title="办公室附近停留",assertion=Assertion.INFERRED),
            Episode("afternoon",startMs=base.windowStartMs+14*3_600_000L,endMs=base.windowStartMs+14*3_600_000L,lane=Lane.LIFE,kind="PHOTO",title="午后影像",assertion=Assertion.RECORDED,photoUris=listOf("content://missing/drum-test")),
            Episode("evening",startMs=base.windowStartMs+20*3_600_000L,endMs=base.windowStartMs+21*3_600_000L,lane=Lane.LIFE,kind="MEDIA",title="晚间音乐",assertion=Assertion.RECORDED))
        return WoUiState(loaded=true,policy=Policy(requested=true),day=base.copy(episodes=entries))
    }
    private fun capture(name:String,frames:Boolean=false) {
        compose.waitForIdle()
        val file=File("build/${if(frames)"drum-frames" else "ui-previews"}/$name.png");file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().buffered().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Composable private fun Shell(s:WoUiState=state(),dark:Boolean=false,open:(Episode)->Unit={},stats:()->Unit={}) {
        WoTheme(dark=dark) { Scaffold(bottomBar={AppNavigation(0){}}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { DayDrumHome(s,open,stats,{},{},{}) }
        } }
    }
    @Test fun homeHasOnlyTheDrumAndStatisticsIsAnExplicitAction() {
        var openedStats=false
        compose.setContent{Shell(stats={openedStats=true})}
        compose.onNodeWithTag("experience-drum").assertIsDisplayed()
        compose.onNodeWithTag("today-list").assertDoesNotExist()
        compose.onNodeWithTag("day-usage-chart").assertDoesNotExist()
        compose.onNodeWithTag("drum-position").assertTextEquals("1 / 3")
        compose.onNodeWithContentDescription("上一段经历").assertIsNotEnabled()
        capture("22-drum-home")
        compose.onNodeWithContentDescription("一天统计").performClick()
        assertTrue(openedStats)
    }
    @Test fun horizontalGestureAndTimeScrubbingOpenTheSelectedExperience() {
        var opened=""
        compose.setContent{Shell(open={opened=it.id})}
        compose.onNodeWithTag("experience-drum").performTouchInput {
            down(Offset(width*.8f,centerY))
            moveTo(Offset(width*.3f,centerY),delayMillis=650)
            advanceEventTime(300)
            up()
        }
        compose.onNodeWithTag("drum-position").assertTextEquals("2 / 3")
        capture("23-drum-photo")
        compose.onNodeWithTag("day-time-scrubber").performSemanticsAction(SemanticsActions.SetProgress){it(.95f)}
        compose.onNodeWithTag("drum-position").assertTextEquals("3 / 3")
        compose.onNodeWithContentDescription("下一段经历").assertIsNotEnabled()
        compose.onNodeWithTag("moment-evening").performClick()
        assertEquals("evening",opened)
        compose.onNodeWithTag("drum-jump-上午").performClick()
        compose.onNodeWithTag("drum-position").assertTextEquals("1 / 3")
        capture("24-drum-return")
    }
    @Test fun restoreAndNewRecordsPreserveTheExperienceBeingViewed() {
        val restore=StateRestorationTester(compose)
        var s by mutableStateOf(state())
        restore.setContent{Shell(s)}
        compose.onNodeWithContentDescription("下一段经历").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("drum-position").assertTextEquals("2 / 3")
        compose.runOnIdle {
            val d=s.day!!
            val first=d.episodes.first().copy(id="earlier",startMs=d.windowStartMs+60_000L,endMs=d.windowStartMs+120_000L)
            s=s.copy(day=d.copy(episodes=listOf(first)+d.episodes))
        }
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("下午 · 14:00")
        compose.onNodeWithTag("drum-position").assertTextEquals("3 / 4")
        compose.runOnIdle { s=s.copy(day=s.day!!.let{it.copy(episodes=it.episodes.filterNot{e->e.id=="afternoon"})}) }
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("晚上 · 20:00")
        compose.onNodeWithTag("drum-position").assertTextEquals("3 / 3")
    }
    @Test fun emptyDayOffersSetupWithoutInventingExperiences() {
        var setup=false
        val s=state().let{it.copy(day=it.day!!.copy(episodes=emptyList(),metrics=emptyList()))}
        compose.setContent{WoTheme(dark=false){Surface{DayDrumHome(s,{},{},{},{setup=true},{})}}}
        compose.onNodeWithTag("experience-drum").assertDoesNotExist()
        compose.onNodeWithText("开启记录来源").performClick()
        assertTrue(setup)
        capture("25-drum-empty")
    }
    @Test @Config(qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
    fun narrowDarkLargeTypeStillRotatesAndOpensStats() {
        var stats=false
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.5f)){Shell(dark=true,stats={stats=true})}
        }
        compose.onNodeWithContentDescription("下一段经历").assertIsDisplayed().performClick()
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("下午 · 14:00")
        capture("26-drum-large-dark")
        compose.onNodeWithContentDescription("一天统计").performClick();assertTrue(stats)
    }
    @Test @Config(qualifiers="zh-rCN-w800dp-h360dp-land-xhdpi")
    fun landscapeKeepsTheExperienceAndTimeControlsVisible() {
        compose.setContent{Shell()}
        compose.onNodeWithText("办公室",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithContentDescription("下一段经历").assertIsDisplayed().performClick()
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("下午 · 14:00")
        compose.onNodeWithTag("day-time-scrubber").assertIsDisplayed()
        capture("29-drum-landscape")
    }
    @Test fun statsPageKeepsChartsAndEpisodeDetailsReachable() {
        var opened=""
        val s=state().copy(day=demoDay(LocalDate.of(2026,10,15),"Asia/Shanghai"))
        compose.setContent{WoTheme(dark=false){Surface{DayStatisticsPage(s,{},{},{opened=it.id})}}}
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("什么时候，打开了什么"))
        compose.onAllNodesWithText("微信")[0].performClick()
        assertTrue(opened.isNotBlank())
        capture("27-separate-statistics")
    }
    @Test fun cylinderHasIntermediateDepthFrames() {
        val s=state().copy(demo=true)
        val day=s.day!!.let{it.copy(episodes=it.episodes.map{e->if(e.id=="afternoon")e.copy(kind="MEDIA",title="午后音乐",photoUris=emptyList())else e})}
        lateinit var pager:PagerState
        compose.setContent { WoTheme(dark=false) { Surface(color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Text("虚构示例 · 转动一天",Modifier.padding(24.dp),style=MaterialTheme.typography.titleMedium)
                pager=rememberPagerState(pageCount={3})
                DayDrum(day,dayMoments(day),pager,{},Modifier.weight(1f))
            }
        } } }
        for(frame in 0..12) {
            val position=frame/12f
            compose.runOnIdle { runBlocking { if(position<=.5f)pager.scrollToPage(0,position) else pager.scrollToPage(1,position-1f) } }
            capture("frame-%02d".format(frame),true)
            if(frame==5)capture("28-drum-depth")
        }
    }
}

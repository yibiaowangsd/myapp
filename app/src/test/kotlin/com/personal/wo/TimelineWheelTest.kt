package com.personal.wo

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.personal.wo.core.*
import com.personal.wo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
class TimelineWheelTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val date=LocalDate.of(2026,10,15)
    private fun show(onOpen:(DayChapter)->Unit={}) {
        val base=demoDay(date,"Asia/Shanghai")
        val episodes=(0..2).map { index ->
            Episode("wheel-$index",startMs=base.windowStartMs+(9+index*4)*3_600_000L,
                endMs=base.windowStartMs+(10+index*4)*3_600_000L,lane=Lane.LIFE,
                kind="STAY",title="第${index+1}段经历",assertion=Assertion.RECORDED)
        }
        val s=WoUiState(loaded=true,selectedDate=date.toString(),demo=true,
            policy=Policy(requested=true,archiveZone="Asia/Shanghai"),day=base.copy(episodes=episodes))
        compose.setContent { WoTheme(dark=false) {
            Box(Modifier.fillMaxSize()) { DayDrumHome(s,onOpen,{},{},{},{},today=date) }
        } }
    }
    @Test fun eventsStayOnOppositeSidesOfTheAxis() {
        show()
        val scene=compose.onNodeWithTag("drum-scene").fetchSemanticsNode().boundsInRoot
        val first=compose.onNodeWithTag("wheel-face-1",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("wheel-face-2",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(first.center.y>scene.center.y)
        assertTrue(second.center.y<scene.center.y)
        compose.onNodeWithContentDescription("下一段经历").performClick()
        val firstAfter=compose.onNodeWithTag("wheel-face-1",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val secondAfter=compose.onNodeWithTag("wheel-face-2",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(firstAfter.center.y>scene.center.y)
        assertTrue(secondAfter.center.y<scene.center.y)
    }
    @Test fun gapIsNotAnInvisibleClickTargetButTheProjectedCardIs() {
        var opened=0
        show{opened++}
        val pager=compose.onNodeWithTag("experience-drum")
        pager.performTouchInput { click(center) }
        assertEquals(0,opened)
        val face=compose.onNodeWithTag("wheel-face-1",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val bounds=pager.fetchSemanticsNode().boundsInRoot
        pager.performTouchInput { click(face.center-bounds.topLeft) }
        assertEquals(1,opened)
    }
    @Test fun draggingFromTheCardDoesNotOpenItsDetails() {
        var opened=0
        show{opened++}
        val pager=compose.onNodeWithTag("experience-drum")
        val face=compose.onNodeWithTag("wheel-face-1",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val bounds=pager.fetchSemanticsNode().boundsInRoot
        val start=face.center-bounds.topLeft
        pager.performTouchInput {
            down(start);moveTo(Offset(start.x-width*.44f,start.y),delayMillis=600)
            advanceEventTime(180);up()
        }
        assertEquals(0,opened)
        compose.onNodeWithTag("drum-position").assertTextEquals("2 / 3 个大事件")
    }
    @Test fun fadeAndProjectionRemainContinuousAcrossTheOldCutoff() {
        for(step in 0..3000) {
            val x=step/1000f
            val left=drumPose(-x);val right=drumPose(x)
            assertEquals(-left.x,right.x,.00001f)
            assertEquals(left.scale,right.scale,.00001f)
            assertEquals(left.alpha,right.alpha,.00001f)
            assertTrue(right.alpha in 0f..1f)
            if(step>0)assertTrue(abs(right.alpha-drumPose(x-.001f).alpha)<.002f)
        }
        assertEquals(0f,drumPose(2.85f).alpha,0f)
    }
    @Test fun scrubberMappingIsContinuousAndHandlesEqualTimestamps() {
        val times=longArrayOf(0,60_000,3_600_000,8_000_000)
        for(step in 0..3000) {
            val position=step/1000f
            assertEquals(position,timelinePosition(times,timelineTime(times,position)),.0001f)
        }
        assertEquals(1.5f,timelinePosition(longArrayOf(0,0,100),50),.0001f)
    }
}

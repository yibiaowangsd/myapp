package com.personal.wo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayDrumUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val date=LocalDate.of(2026,10,15)
    private fun state():WoUiState {
        val base=demoDay(date,"Asia/Shanghai")
        fun e(id:String,start:Int,end:Int,kind:String,title:String)=Episode(id,startMs=base.windowStartMs+start*60_000L,endMs=base.windowStartMs+end*60_000L,
            lane=if(kind=="APP")Lane.DIGITAL else Lane.LIFE,kind=kind,title=title,assertion=Assertion.RECORDED)
        val entries=listOf(
            e("morning",540,660,"STAY","办公室附近停留").copy(assertion=Assertion.INFERRED),
            e("long-app",555,590,"APP","浏览器"),e("short-app",600,602,"APP","短暂打开"),
            e("photo-one",559,559,"PHOTO","上午影像").copy(photoUris=listOf("content://fixture/one")),
            e("photo-two",587,587,"PHOTO","另一项影像").copy(photoUris=listOf("content://fixture/two")),
            e("afternoon",840,840,"PHOTO","午后影像").copy(photoUris=listOf("content://fixture/one")),
            e("evening",1200,1260,"MEDIA","晚间音乐"))
        return WoUiState(loaded=true,selectedDate=date.toString(),policy=Policy(requested=true,archiveZone="Asia/Shanghai"),day=base.copy(episodes=entries),demo=true)
    }
    private fun seedImages() {
        // Obvious synthetic fixtures, never presented as a user's photos or stored in the repository.
        listOf("one","two").forEachIndexed { i,key ->
            val bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)
            val c=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG)
            p.color=if(i==0)0xffd4dfd9.toInt() else 0xffeed7b8.toInt();c.drawRect(0f,0f,400f,400f,p)
            p.color=if(i==0)0xff608b7a.toInt() else 0xffba8058.toInt();c.drawCircle(270f,130f,130f,p);c.drawRect(0f,270f,400f,400f,p)
            p.color=0xffffffff.toInt();p.textSize=28f;c.drawText("SAMPLE IMAGE ${i+1}",30f,340f,p)
            ThumbnailCache.cache.put("content://fixture/$key",bitmap)
        }
    }
    private fun capture(name:String,frames:Boolean=false) {
        compose.waitForIdle()
        val file=File("build/${if(frames)"drum-frames" else "ui-previews"}/$name.png");file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull{it.isShown} ?: compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));file.outputStream().buffered().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Composable private fun Shell(s:WoUiState=state(),dark:Boolean=false,open:(DayChapter)->Unit={},stats:()->Unit={},onDate:(LocalDate)->Unit={},threshold:(Int)->Unit={}) {
        WoTheme(dark=dark) { Scaffold(bottomBar={AppNavigation(0){}}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { DayDrumHome(s,open,stats,{},{},{},onDate,threshold,today=date) }
        } }
    }
    @Test fun homeGroupsPeriodsAndOpensStatisticsExplicitly() {
        seedImages();var openedStats=false
        compose.setContent{Shell(stats={openedStats=true})}
        compose.onNodeWithTag("experience-drum").assertIsDisplayed()
        compose.onNodeWithTag("today-list").assertDoesNotExist()
        compose.onNodeWithTag("day-usage-chart").assertDoesNotExist()
        compose.onNodeWithTag("drum-position").assertTextEquals("1 / 3 个时段")
        compose.onNodeWithTag("chapter-open").assertContentDescriptionContains("浏览器35分钟",substring=true)
        compose.onNodeWithTag("chapter-open").assertContentDescriptionContains("2项影像",substring=true)
        compose.onNodeWithContentDescription("上一段经历").assertIsEnabled()
        capture("22-chapter-home")
        compose.onNodeWithContentDescription("一天统计").performClick();assertTrue(openedStats)
    }
    @Test fun horizontalGestureAndScrubbingOpenWholePeriod() {
        seedImages();var opened=""
        compose.setContent{Shell(open={opened=it.part.label})}
        compose.onNodeWithTag("experience-drum").performTouchInput {
            down(Offset(width*.8f,centerY));moveTo(Offset(width*.3f,centerY),delayMillis=650);advanceEventTime(300);up()
        }
        compose.onNodeWithTag("drum-position").assertTextEquals("2 / 3 个时段")
        capture("23-chapter-middle")
        compose.onNodeWithTag("day-time-scrubber").performSemanticsAction(SemanticsActions.SetProgress){it(.95f)}
        compose.onNodeWithTag("drum-position").assertTextEquals("3 / 3 个时段")
        compose.onNodeWithTag("chapter-open").performClick();assertEquals("晚上",opened)
        compose.onNodeWithTag("drum-jump-上午").performClick()
        compose.onNodeWithTag("drum-position").assertTextEquals("1 / 3 个时段")
    }
    @Test fun restoreAndDynamicPartsPreserveTheSelectedPeriod() {
        val restore=StateRestorationTester(compose);var s by mutableStateOf(state())
        restore.setContent{Shell(s)}
        compose.onNodeWithContentDescription("下一段经历").performClick();restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("drum-position").assertTextEquals("2 / 3 个时段")
        compose.runOnIdle {
            val d=s.day!!;val earlier=d.episodes.first().copy(id="earlier",startMs=d.windowStartMs+60_000L,endMs=d.windowStartMs+120_000L)
            s=s.copy(day=d.copy(episodes=listOf(earlier)+d.episodes))
        }
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("下午 · 12:00–18:00")
        compose.onNodeWithTag("drum-position").assertTextEquals("3 / 4 个时段")
        compose.runOnIdle {s=s.copy(day=s.day!!.let{it.copy(episodes=it.episodes.filterNot{e->e.id=="afternoon"})})}
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("晚上 · 18:00–21:30")
    }
    @Test fun emptyHistoricalDayKeepsDateControlsWithoutInventingRecords() {
        var setup=false;var selected:LocalDate?=null
        val s=state().let{it.copy(day=it.day!!.copy(episodes=emptyList(),metrics=emptyList()),demo=false)}
        compose.setContent{WoTheme(dark=false){Surface{DayDrumHome(s,{},{},{},{setup=true},{},{selected=it},today=date)}}}
        compose.onNodeWithTag("experience-drum").assertDoesNotExist()
        compose.onNodeWithText("开启记录来源").performClick();assertTrue(setup)
        compose.onNodeWithText("前一天").performClick();assertEquals(date.minusDays(1),selected)
        compose.onNodeWithText("后一天").assertIsNotEnabled();capture("25-chapter-empty")
    }
    @Test @Config(qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
    fun narrowDarkLargeTypeStillRotatesAndOpensStats() {
        seedImages();var stats=false
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.5f)){Shell(dark=true,stats={stats=true})}
        }
        capture("26-chapter-large-dark")
        compose.onNodeWithContentDescription("下一段经历").assertIsDisplayed().performClick()
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("下午 · 12:00–18:00")
        compose.onNodeWithContentDescription("一天统计").performClick();assertTrue(stats)
    }
    @Test @Config(qualifiers="zh-rCN-w800dp-h360dp-land-xhdpi")
    fun landscapeKeepsPeriodAndTimeControlsVisible() {
        seedImages();compose.setContent{Shell()}
        compose.onNodeWithContentDescription("下一段经历").assertIsDisplayed().performClick()
        compose.onNodeWithTag("drum-selected-time").assertTextEquals("下午 · 12:00–18:00")
        compose.onNodeWithTag("day-time-scrubber").assertIsDisplayed();capture("29-chapter-landscape")
    }
    @Test fun statsPageKeepsChartsAndEpisodeDetailsReachable() {
        var opened="";val s=state().copy(day=demoDay(date,"Asia/Shanghai"))
        compose.setContent{WoTheme(dark=false){Surface{DayStatisticsPage(s,{},{},{opened=it.id})}}}
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("什么时候，打开了什么"))
        compose.onAllNodesWithText("微信")[0].performClick();assertTrue(opened.isNotBlank());capture("27-separate-statistics")
    }
    @Test fun dateEdgesCalendarAndTodayAreReachable() {
        var selected:LocalDate?=null
        compose.setContent{Shell(onDate={selected=it})}
        compose.onNodeWithContentDescription("上一段经历").performClick()
        compose.onNodeWithTag("chapter-open").performClick();assertEquals(date.minusDays(1),selected)
        compose.onNodeWithText("回到今天").performClick();assertEquals(date,selected)
        compose.onNodeWithTag("drum-date",useUnmergedTree=true).performClick()
        compose.onNodeWithText("选择回看的日期").assertIsDisplayed();capture("30-chapter-calendar")
        compose.onNodeWithText("回看这一天").performClick();assertEquals(date,selected)
    }
    @Test fun loadingHistoricalDateDoesNotShowPreviousDaysRecords() {
        compose.setContent{Shell(state().copy(selectedDate=date.minusDays(1).toString(),busy=true))}
        compose.onNodeWithTag("experience-drum").assertDoesNotExist()
        compose.onNodeWithContentDescription("一天统计").assertIsNotEnabled()
        compose.onNodeWithTag("drum-date",useUnmergedTree=true).assertTextEquals("2026年10月14日")
    }
    @Test fun thresholdChoiceDoesNotChangePageOrRemoveRawDetails() {
        var s by mutableStateOf(state())
        compose.setContent{Shell(s,threshold={s=s.copy(homeAppMinMinutes=it)})}
        compose.onNodeWithContentDescription("时间轴选项").performClick()
        compose.onNodeWithText("应用显示门槛 · 10分钟").performClick();capture("31-chapter-filter")
        compose.onNodeWithText("至少 30 分钟").performClick()
        compose.onNodeWithTag("drum-position").assertTextEquals("1 / 3 个时段")
        assertEquals(30,s.homeAppMinMinutes)
        assertTrue(dayChapters(s.day!!,30).first().part.episodes.any{it.id=="short-app"})
    }
    @Test fun detailsShowAllImagesAndShortUsageAndOpenOriginalEvidence() {
        seedImages();val s=state();val day=s.day!!;val chapter=dayChapters(day).first();var opened=""
        compose.setContent{WoTheme(dark=false){Surface{ChapterDetailsPage(day,chapter,10){opened=it.id}}}}
        compose.onNodeWithText("这一段的影像").assertIsDisplayed();capture("32-chapter-details")
        compose.onAllNodesWithContentDescription("关联媒体缩略图")[0].performClick();assertEquals("photo-one",opened)
        compose.onNodeWithTag("chapter-details").performScrollToNode(hasText("短暂打开"))
        compose.onNodeWithText("短暂打开").performClick();assertEquals("short-app",opened)
    }
    @Test fun bothSideFacesAreActuallyDrawnAtMiddleAndPoseIsSymmetric() {
        seedImages();compose.setContent{Shell()}
        compose.onNodeWithContentDescription("下一段经历").performClick()
        val scene=compose.onNodeWithTag("drum-scene").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val view=compose.activity.window.decorView;val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b))
            val background=b.getPixel((scene.left+4).toInt(),(scene.top+5).toInt())
            fun distance(a:Int,z:Int)=kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(z))+kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(z))+kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(z))
            for(x in listOf(scene.left+36,scene.right-36)) {
                val different=(-40..40).count{dy->distance(b.getPixel(x.toInt(),(scene.center.y+dy).toInt()),background)>8}
                assertTrue("A side card must be visibly drawn, x=$x, changed=$different",different>50)
            }
            b.recycle()
        }
        for(offset in listOf(.25f,.5f,1f,1.25f)) {
            val l=drumPose(-offset);val r=drumPose(offset)
            assertEquals(-l.x,r.x,.0001f);assertEquals(-l.rotation,r.rotation,.0001f)
            assertEquals(l.scale,r.scale,.0001f);assertEquals(l.alpha,r.alpha,.0001f);assertEquals(l.y,r.y,.0001f)
        }
        capture("24-both-sides-visible")
    }
    @Test fun cylinderHasDepthFramesInBothDirections() {
        seedImages();val day=state().day!!;val chapters=dayChapters(day);lateinit var pager:PagerState
        compose.setContent{WoTheme(dark=false){Surface(color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize()) {
            Text("虚构示例 · 左右转动一天",Modifier.padding(24.dp),style=MaterialTheme.typography.titleMedium)
            pager=rememberPagerState(initialPage=2,pageCount={chapters.size+2})
            DayDrum(day,chapters,pager,{},{},date,Modifier.weight(1f))
        }}}}
        for(frame in 0..24) {
            val position=1f+frame/12f;val page=position.roundToInt()
            compose.runOnIdle {runBlocking{pager.scrollToPage(page,position-page)}}
            capture("frame-%02d".format(frame),true)
            if(frame==5)capture("28-chapter-depth-left")
            if(frame==19)capture("33-chapter-depth-right")
        }
    }
}

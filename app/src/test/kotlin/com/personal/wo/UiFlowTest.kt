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
import com.personal.wo.collector.ConnectedApp
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
    @Test fun permissionsOpenSystemDirectlyAndOfferOneBatchAction() {
        var requests=0
        var batchRequests=0
        val current=WoUiState(loaded=true,sources=Source.entries.map{SourceState(it)})
        compose.setContent { WoTheme(dark=false) { PermissionCenter(current,{requests++},{_,_->},{},{},{},{},{batchRequests++},{}) } }
        capture("01-permissions")
        compose.onNodeWithTag("request-all").performClick()
        assertEquals(1,batchRequests)
        compose.onAllNodesWithText("去授权")[0].performClick()
        assertEquals(1,requests)
        compose.onNodeWithTag("confirm-permission").assertDoesNotExist()
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
        compose.onNodeWithText("按时间回看").assertIsDisplayed()
        compose.onNodeWithText("上午",substring=false).assertIsDisplayed()
        compose.onNodeWithText("一天的数据").assertIsNotDisplayed()
        capture("03-today")
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("day-usage-chart"))
        capture("18-day-chart")
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("什么时候，打开了什么"))
        capture("04-visuals")
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("办公室"))
        compose.onNodeWithText("办公室",substring=false).performClick()
        assertEquals("办公室附近停留",selected?.title)
    }
    @Test fun emptyDashboardOffersSetupAndDoesNotInventMeasurements() {
        var setup=false
        val d=state().day!!.copy(episodes=emptyList(),metrics=emptyList(),sentences=emptyList())
        compose.setContent { WoTheme(dark=false) { TodayDashboard(WoUiState(loaded=true,day=d),{},{setup=true},{},{},{},{}) } }
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("查看记录权限"))
        compose.onNodeWithText("查看记录权限").performClick()
        assertTrue(setup)
        compose.onNodeWithText("4,280 步").assertDoesNotExist()
        capture("05-empty")
    }
    @Test @Config(qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
    fun narrowScreenAndLargeTextKeepSetupAccessible() {
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.5f)) {
                WoTheme(dark=false) { PermissionCenter(WoUiState(loaded=true,sources=Source.entries.map{SourceState(it)}),{},{_,_->},{},{},{}) }
            }
        }
        compose.onNodeWithTag("request-all").assertIsDisplayed().performClick()
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
    @Test fun photosStayInTheirTimePartAndCanOpenEvenWhenFileIsUnavailable() {
        var selected:Episode?=null
        val d=state().day!!
        val photo=Episode("test-photo",startMs=d.windowStartMs+13*3_600_000,endMs=d.windowStartMs+13*3_600_000,
            lane=Lane.LIFE,kind="PHOTO",title="下午照片",assertion=Assertion.RECORDED,photoUris=listOf("content://missing/photo"))
        val part=recapParts(d.copy(episodes=listOf(photo))).single()
        compose.setContent { WoTheme(dark=false) { RecapTimelineCard(part,d.zoneId){selected=it} } }
        compose.onNodeWithText("下午").assertIsDisplayed()
        compose.waitUntil(timeoutMillis=5000) { compose.onAllNodesWithText("照片暂不可访问").fetchSemanticsNodes().size==1 }
        compose.onNodeWithText("照片暂不可访问").performClick()
        assertEquals("test-photo",selected?.id)
        capture("02-photo-timeline")
    }
    @Test fun stepConnectionFailureOffersRetryWithoutHealthConnect() {
        var connected=false
        val s=state().copy(demo=false,sources=listOf(SourceState(Source.STEPS,authorization="GRANTED",runtime="ERROR",detail="计步传感器未能连接")))
        compose.setContent { WoTheme(dark=false) { TodayDashboard(s,{},{},{},{},{},{},{connected=true}) } }
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("重连"))
        compose.onNodeWithText("重连").performClick()
        assertTrue(connected)
        capture("11-step-retry")
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
    @Test @Config(qualifiers="zh-rCN-w360dp-h812dp-xhdpi")
    fun homeOpensPhotoRecapBeforeStatisticsAndKeepsAllFragmentsReachable() {
        var all=false
        val d=state().day!!
        fun photo(id:String,minute:Int)=Episode(id,startMs=d.windowStartMs+minute*60_000L,endMs=d.windowStartMs+minute*60_000L,
            lane=Lane.LIFE,kind="PHOTO",title="测试影像",assertion=Assertion.RECORDED,photoUris=listOf("content://missing/$id"))
        val entries=listOf(d.episodes.first{it.kind=="APP"}.copy(startMs=d.windowStartMs+60_000,endMs=d.windowStartMs+180_000),photo("one",559),photo("two",587),photo("three",736),photo("four",965))
        val s=state().copy(demo=false,day=d.copy(episodes=entries,asOfMs=d.windowStartMs+993*60_000L))
        compose.setContent { WoTheme(dark=false) {
            Scaffold(bottomBar={AppNavigation(0){}}) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
                    Text("今天",Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.titleLarge)
                    TodayDashboard(s,{all=true},{},{},{},{},{})
                }
            }
        } }
        compose.onNodeWithTag("recap-凌晨").assertIsDisplayed()
        compose.onNodeWithTag("recap-上午").assertIsDisplayed()
        compose.onNodeWithText("一天的数据").assertIsNotDisplayed()
        compose.onNodeWithText("全部片段").performClick()
        assertTrue(all)
        capture("12-timeline-home")
    }
    @Test fun photoGroupsExpandWithoutDroppingHiddenImages() {
        var opened=""
        val d=state().day!!
        val photos=(0..4).map { i->Episode("photo-$i",startMs=d.windowStartMs+(540+i)*60_000L,endMs=d.windowStartMs+(540+i)*60_000L,
            lane=Lane.LIFE,kind="PHOTO",title="测试照片 $i",assertion=Assertion.RECORDED,photoUris=listOf("content://missing/expand-$i")) }
        compose.setContent { WoTheme(dark=false) {
            androidx.compose.foundation.lazy.LazyColumn { item { RecapTimelineCard(recapParts(d.copy(episodes=photos)).single(),d.zoneId){opened=it.id} } }
        } }
        compose.onNodeWithText("09:04").assertDoesNotExist()
        compose.onNodeWithText("展开全部 5 项影像").performClick()
        compose.onNodeWithText("09:04").performScrollTo().performClick()
        assertEquals("photo-4",opened)
        compose.onNodeWithText("收起影像").performScrollTo().performClick()
        compose.onNodeWithText("09:04").assertDoesNotExist()
    }
    @Test fun hourlyChartShowsUnionTotalAndSelectedHourAndNoInventedData() {
        val d=state().day!!
        compose.setContent { WoTheme(dark=false) { Surface { DayUsageChart(d) } } }
        compose.onNodeWithText("2小时5分钟").assertIsDisplayed()
        compose.onNodeWithContentDescription("前一小时").performClick()
        compose.onNodeWithTag("hour-selection").assertTextContains("09:00–10:00 · 30分钟")
        compose.onNodeWithContentDescription("后一小时").performClick()
        compose.onNodeWithTag("hour-selection").assertTextContains("10:00–11:00 · 40分钟")
        capture("19-hour-detail")
    }
    @Test fun chartWithoutUsagePermissionDoesNotRenderZeroBars() {
        val d=state().day!!.copy(metrics=emptyList(),episodes=emptyList())
        compose.setContent { WoTheme(dark=false) { DayUsageChart(d) } }
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithTag("hour-chart").assertDoesNotExist()
    }
    @Test @Config(qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
    fun recapAndChartsRemainUsableWithLargeTextInDarkMode() {
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.5f)) {
                WoTheme(dark=true) { Surface(color=MaterialTheme.colorScheme.background) {
                    Box(Modifier.padding(horizontal=16.dp)) { TodayDashboard(state(),{},{},{},{},{},{}) }
                } }
            }
        }
        compose.onNodeWithText("全部片段").assertIsDisplayed()
        capture("20-recap-large-dark")
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("day-usage-chart"))
        compose.onNodeWithTag("today-list").performScrollToNode(hasContentDescription("后一小时"))
        compose.onNodeWithContentDescription("后一小时").assertIsDisplayed().performClick()
        capture("21-chart-large-dark")
    }
    @Test fun connectionSelectionUsesInstalledAppsAndRequestsSystemAccessDirectly() {
        var selected="";var permission:Source?=null
        val s=state().copy(demo=false,appsLoaded=true,apps=listOf(ConnectedApp("com.tencent.qqmusic","QQ 音乐","音乐与播客"),ConnectedApp("com.MobileTicket","铁路 12306","出行与地图")))
        compose.setContent { WoTheme(dark=false) { Surface(color=MaterialTheme.colorScheme.background) {
            ConnectionsPage(s,{permission=it},{_,_->},{_,_->},{pkg,on->if(on)selected=pkg},{},{},{},{})
        } } }
        capture("13-connections")
        compose.onNodeWithText("搜索应用或类别").performTextInput("铁路")
        compose.onNodeWithText("QQ 音乐").assertDoesNotExist()
        compose.onNodeWithText("铁路 12306").performClick()
        compose.onNodeWithTag("notice-com.MobileTicket").performClick()
        assertEquals("com.MobileTicket",selected);assertEquals(Source.NOTIFICATION,permission)
        capture("14-app-connection")
    }
    @Test fun sourcePageDisclosesHuaweiAccountNotConnectedAndShowsBackgroundHelp() {
        val s=state().copy(demo=false,appsLoaded=true,sources=listOf(SourceState(Source.HEALTH,supported=false)),
            huawei=com.personal.wo.health.HuaweiState(com.personal.wo.health.HuaweiPhase.NOT_CONFIGURED,detail="尚未提供真实App ID和获批读取范围"))
        compose.setContent { WoTheme(dark=false) { ConnectionsPage(s,{},{_,_->},{_,_->},{_,_->},{},{},{},{}) } }
        compose.onNodeWithText("来源与后台").performClick()
        compose.onNodeWithTag("connections-list").performScrollToNode(hasText("待平台开通"))
        compose.onNodeWithText("待平台开通").assertIsDisplayed()
        compose.onNodeWithText("华为账号授权").assertDoesNotExist()
        capture("15-huawei-status")
        compose.onNodeWithTag("connections-list").performScrollToNode(hasText("需要一直开着吗？"))
        compose.onNodeWithTag("connections-list").performScrollToIndex(3)
        compose.onNodeWithText("电池优化").assertIsDisplayed()
        capture("16-background-help")
    }
    @Test @Config(qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
    fun connectionControlsRemainAccessibleWithLargeTextAndDarkTheme() {
        var excluded=false
        val s=state().copy(demo=false,appsLoaded=true,apps=listOf(ConnectedApp("com.tencent.qqmusic","QQ 音乐","音乐与播客")))
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.5f)) {
                WoTheme(dark=true) { Surface(color=MaterialTheme.colorScheme.background) {
                    ConnectionsPage(s,{},{_,_->},{_,on->excluded=!on},{_,_->},{},{},{},{})
                } }
            }
        }
        compose.onNodeWithText("QQ 音乐").performClick()
        compose.onNodeWithTag("connections-list").performScrollToNode(hasTestTag("include-com.tencent.qqmusic"))
        compose.onNodeWithTag("include-com.tencent.qqmusic").performClick()
        assertTrue(excluded)
        capture("17-connections-large-dark")
    }

}

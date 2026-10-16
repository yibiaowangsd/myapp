package com.personal.wo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
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
class AchievementsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun fixture(demo:Boolean=false):WoUiState {
        val date=LocalDate.of(2026,10,15);val base=demoDay(date,"Asia/Shanghai")
        val reading=Episode("reading-fixture",startMs=base.windowStartMs+600*60_000L,endMs=base.windowStartMs+660*60_000L,lane=Lane.DIGITAL,kind="APP",assertion=Assertion.RECORDED,title="虚构阅读记录",packageName="com.tencent.weread",evidence=listOf(Evidence("reading",Source.USAGE,"reading","虚构测试依据")))
        val day=base.copy(episodes=base.episodes+reading)
        val snapshot=achievementSnapshot(listOf(day),policy=Policy(archiveZone=day.zoneId),demo=demo)
        return WoUiState(loaded=true,day=day,history=listOf(day),selectedDate=day.dayDate,policy=Policy(archiveZone=day.zoneId),achievements=snapshot,demo=demo,appsLoaded=true)
    }
    @Composable private fun screen(s:WoUiState=fixture(),create:(AchievementGoal)->Unit={},pin:(String)->Unit={},read:(Set<String>)->Unit={},seen:(Set<String>)->Unit={}) {
        WoTheme(dark=false){Surface{Box(Modifier.fillMaxSize().padding(horizontal=16.dp)){
            AchievementsPage(s,create,{},read,pin,seen,{},{},{})
        }}}
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull{it.isShown}?:compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));val file=File("build/ui-previews/$name.png");file.parentFile!!.mkdirs()
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun galleryShowsActualCountsAndCollectsNewAwardsOnce() {
        val s=fixture();var ids=emptySet<String>()
        compose.setContent{screen(s,seen={ids=it})}
        compose.onNodeWithTag("achievement-count").assertTextEquals("${s.achievements!!.unlocked.size} 枚已点亮 · 32 枚可收藏")
        capture("achievements-gallery")
        compose.onNodeWithTag("achievement-acknowledge").performClick();assertEquals(s.achievements.newIds,ids)
    }
    @Test fun detailExplainsApproximateReadingAndAllowsShowcase() {
        var pinned=""
        compose.setContent{screen(pin={pinned=it})}
        compose.onNodeWithTag("achievement-gallery").performScrollToKey("reading-1")
        compose.onNodeWithTag("medal-reading-1").performClick()
        compose.onNodeWithTag("achievement-rule").assertTextEquals("累计阅读应用前台使用 30 分钟")
        compose.onNodeWithText("不会读取微信读书账号，也不导入第三方官方勋章。").assertIsDisplayed()
        capture("achievements-reading-detail")
        compose.onNodeWithText("放到我的展台（最多3枚）").performScrollTo().performClick();assertEquals("reading-1",pinned)
    }
    @Test fun customGoalNeedsValidNameAndPositiveTarget() {
        var saved:AchievementGoal?=null
        compose.setContent{screen(create={saved=it})}
        compose.onNodeWithTag("create-achievement").performClick()
        compose.onNodeWithTag("custom-achievement-save").assertIsNotEnabled()
        compose.onNodeWithTag("custom-achievement-name").performTextInput("慢慢成为自己")
        compose.onNodeWithTag("custom-achievement-target").performScrollTo().performTextInput("7")
        capture("achievements-create")
        compose.onNodeWithTag("custom-achievement-save").performClick()
        assertEquals("慢慢成为自己",saved?.name);assertEquals(7L,saved?.target);assertTrue(saved!!.custom)
    }
    @Test fun demoHasSeparateLabelAndCannotWriteAchievements() {
        compose.setContent{screen(fixture(true))}
        compose.onNodeWithText("虚构演示 · 不写入真实成就").assertIsDisplayed()
        compose.onNodeWithTag("create-achievement").assertIsNotEnabled()
        compose.onNodeWithText("阅读应用").assertIsNotEnabled()
        compose.onNodeWithTag("achievement-acknowledge").assertDoesNotExist()
    }
    @Test fun customFilterHasHelpfulEmptyState() {
        compose.setContent{screen()}
        compose.onNodeWithTag("achievement-filter-CUSTOM").performClick()
        compose.onNodeWithText("还没有自定义成就。给自己的日常取一个特别的名字。").assertIsDisplayed()
    }
    @Test fun readingApplicationsCanBeExplicitlyDeselected() {
        var packages:Set<String>?=null
        compose.setContent{screen(read={packages=it})}
        compose.onNodeWithText("阅读应用").performClick()
        compose.onNodeWithText("微信读书").performClick()
        compose.onNodeWithText("保存").performClick()
        assertEquals(emptySet<String>(),packages)
    }
    @Test fun meEntryIsIntegratedWithoutRemovingPrivacyAndPermissions() {
        var open=false
        compose.setContent{WoTheme(dark=false){Surface{MePage(fixture(),{},{},{},{},{},achievementContent={AchievementPreview(fixture().achievements){open=true}})}}}
        compose.onNodeWithTag("achievement-entry").performScrollTo().performClick();assertTrue(open)
        capture("achievements-me")
        compose.onNodeWithText("数据与隐私").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
    fun largeTypeAndDarkThemeKeepControlsUsable() {
        compose.setContent{val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.5f)) {
                WoTheme(dark=true){Surface{Box(Modifier.fillMaxSize().padding(16.dp)){AchievementsPage(fixture(),{},{},{},{},{},{},{},{})}}}
            }
        }
        compose.onNodeWithTag("create-achievement").assertIsDisplayed().performClick()
        compose.onNodeWithTag("custom-achievement-name").assertIsDisplayed()
        capture("achievements-large-dark")
    }
    @Test @Config(qualifiers="zh-rCN-w800dp-h1480dp-mdpi")
    fun renderAllThirtyTwoVectorBadgeDesigns() {
        compose.setContent{WoTheme(dark=false){Surface{Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("我 · 日常成就",style=MaterialTheme.typography.headlineMedium)
            Text("原创矢量徽章 · 8个系列 / 4个阶段 · 全点亮设计展示，不代表真实获得",style=MaterialTheme.typography.bodyMedium)
            AchievementMetric.entries.forEach { metric ->
                Text(metric.label,style=MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    achievementCatalog.filter{it.metric==metric}.forEach { goal ->
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                            AchievementBadge(metric,goal.tier,true,Modifier.size(103.dp))
                            Text(goal.name,style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }}}}
        capture("achievements-badge-atlas")
    }
}

package com.personal.wo.benchmark

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.benchmark.macro.MacrobenchmarkScope

internal const val PACKAGE="com.personal.wo"
/** Run only on a dedicated test device. Uses the app's explicitly labelled synthetic demo. */
internal fun prepareTestApp() {
    val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    listOf("ACTIVITY_RECOGNITION","ACCESS_COARSE_LOCATION","ACCESS_FINE_LOCATION","READ_EXTERNAL_STORAGE","READ_MEDIA_IMAGES","READ_MEDIA_VIDEO","READ_MEDIA_VISUAL_USER_SELECTED","READ_CALENDAR","POST_NOTIFICATIONS").forEach {
        device.executeShellCommand("pm grant $PACKAGE android.permission.$it")
    }
    device.executeShellCommand("appops set $PACKAGE GET_USAGE_STATS allow")
    device.executeShellCommand("cmd notification allow_listener $PACKAGE/.collector.NoticeListener")
    val context=InstrumentationRegistry.getInstrumentation().context
    context.startActivity(context.packageManager.getLaunchIntentForPackage(PACKAGE)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    check(device.wait(Until.hasObject(By.res("day-drum-home")),20_000)){"Main screen did not become ready"}
    device.pressHome()
}
internal fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.res("day-drum-home")),15_000)){"Home did not become ready"}
}
internal fun MacrobenchmarkScope.openDemo() {
    waitForHome()
    if(!device.hasObject(By.res("experience-drum"))) {
        val demo=device.wait(Until.findObject(By.text("转动示例看看")),5_000)
        check(demo!=null){"Labelled demonstration entry is missing"};demo.click()
    }
    check(device.wait(Until.hasObject(By.res("experience-drum")),10_000)){"Timeline did not become ready"}
}
internal fun MacrobenchmarkScope.turnTimeline() {
    val bounds=device.findObject(By.res("experience-drum")).visibleBounds
    val y=bounds.centerY()
    repeat(3) {
        device.swipe(bounds.right-bounds.width()/5,y,bounds.left+bounds.width()/5,y,24)
        device.waitForIdle()
        device.swipe(bounds.left+bounds.width()/5,y,bounds.right-bounds.width()/5,y,24)
        device.waitForIdle()
    }
}
internal fun MacrobenchmarkScope.changeDateAndTabs() {
    device.findObject(By.text("前一天")).click();waitForHome()
    device.findObject(By.text("后一天")).click();waitForHome()
    device.findObject(By.text("成就")).click();device.waitForIdle()
    device.findObject(By.text("设置")).click();device.waitForIdle()
    device.findObject(By.text("首页")).click();waitForHome()
}

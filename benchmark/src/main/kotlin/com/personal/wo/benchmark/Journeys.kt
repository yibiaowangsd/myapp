package com.personal.wo.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

internal const val PACKAGE = "com.personal.wo"
private const val READY_TIMEOUT = 20_000L
private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")
private fun testDevice() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/** Destructive to this package's data: use only on a dedicated benchmark device. */
internal fun resetTestApp() {
    val result = testDevice().executeShellCommand("pm clear $PACKAGE").trim()
    check(result == "Success") { "Could not reset benchmark app: $result" }
}

private fun launchTestApp() {
    InstrumentationRegistry.getInstrumentation().context.startActivity(
        Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}

internal fun UiDevice.required(selector: BySelector, label: String): UiObject2 =
    wait(Until.findObject(selector), READY_TIMEOUT)
        ?: error("$label did not become ready (foreground: $currentPackageName)")

private fun UiDevice.requireHome() {
    required(By.res("day-drum-home"), "Home")
    // The header can update before the new day's projection has arrived.
    required(By.res("drum-date"), "Loaded day projection")
    check(!hasObject(By.res("public-welcome"))) { "Welcome still covers home" }
}

/** Complete onboarding through the public UI, never through private preferences or launch extras. */
private fun UiDevice.finishWelcomeIfPresent() {
    required(By.res(Pattern.compile("public-welcome|day-drum-home")), "Welcome or home")
    if (hasObject(By.res("public-welcome"))) {
        val skip = findObject(By.res("welcome-skip"))
            ?: required(By.res("public-welcome"), "Welcome").scrollUntil(
                Direction.DOWN, Until.findObject(By.res("welcome-skip")))
        checkNotNull(skip) { "Welcome skip entry is missing after scrolling" }.click()
        check(wait(Until.gone(By.res("public-welcome")), READY_TIMEOUT)) { "Welcome did not close" }
    }
    requireHome()
}

/** Both a new install and a returning user must reach the real, fully loaded home. */
internal fun prepareTestApp() {
    val device = testDevice()
    launchTestApp()
    device.finishWelcomeIfPresent()
    device.executeShellCommand("am force-stop $PACKAGE")
    launchTestApp()
    // This relaunch must NOT auto-dismiss welcome: it verifies that skip persisted.
    device.requireHome()
    device.pressHome()
}

internal fun MacrobenchmarkScope.waitForWelcome() {
    device.required(By.res("public-welcome"), "First-run welcome")
    device.required(By.text("让今天\n成为回忆。"), "Welcome content")
    check(!device.hasObject(By.res("day-drum-home"))) { "First run incorrectly bypassed welcome" }
}

internal fun MacrobenchmarkScope.waitForHome() = device.requireHome()

internal fun MacrobenchmarkScope.openDemo() {
    waitForHome()
    if (!device.hasObject(By.res("experience-drum"))) {
        device.required(By.res("open-demo"), "Labelled demonstration entry").click()
    }
    device.required(By.res("experience-drum"), "Timeline")
    device.required(By.text("生活胶片 · 虚构演示"), "Synthetic demo label")
}

internal fun MacrobenchmarkScope.turnTimeline() {
    repeat(3) {
        val bounds = device.required(By.res("experience-drum"), "Timeline").visibleBounds
        val before = device.required(By.res("drum-position"), "Timeline position").text
        check(device.swipe(bounds.right - bounds.width() / 5, bounds.centerY(),
            bounds.left + bounds.width() / 5, bounds.centerY(), 24))
        check(device.wait(Until.gone(By.res("drum-position").text(before)), READY_TIMEOUT)) {
            "Forward swipe did not move the timeline"
        }
        device.waitForIdle()
        val after = device.required(By.res("drum-position"), "Timeline position").text
        check(device.swipe(bounds.left + bounds.width() / 5, bounds.centerY(),
            bounds.right - bounds.width() / 5, bounds.centerY(), 24))
        check(device.wait(Until.gone(By.res("drum-position").text(after)), READY_TIMEOUT)) {
            "Reverse swipe did not move the timeline"
        }
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.changeDateAndTabs() {
    val original = LocalDate.parse(device.required(By.res("drum-date"), "Loaded date").text, dateFormat)
    device.required(By.res("date-previous").enabled(true), "Previous date").click()
    device.required(By.res("drum-date").text(original.minusDays(1).format(dateFormat)), "Previous day projection")
    device.required(By.res("date-next").enabled(true), "Next date").click()
    device.required(By.res("drum-date").text(original.format(dateFormat)), "Restored day projection")
    device.required(By.res("nav-achievements"), "Achievements tab").click()
    device.required(By.res("achievement-gallery"), "Achievements gallery")
    device.required(By.res("achievement-count").textContains("枚已点亮"), "Loaded achievements")
    device.required(By.res("nav-settings"), "Settings tab").click()
    device.required(By.res("settings-page"), "Settings page")
    device.required(By.res("nav-home"), "Home tab").click()
    waitForHome()
}

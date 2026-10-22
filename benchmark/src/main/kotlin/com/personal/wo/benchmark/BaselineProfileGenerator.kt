package com.personal.wo.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule=BaselineProfileRule()
    @Before fun reset()=resetTestApp()
    @Test fun firstRunStartup()=rule.collect(packageName=PACKAGE,includeInStartupProfile=true) {
        // Leave welcome uncompleted so every collection iteration follows first-run UI.
        pressHome();startActivityAndWait();waitForWelcome()
    }
    @Test fun startup() {
        prepareTestApp()
        rule.collect(packageName=PACKAGE,includeInStartupProfile=true) {
        pressHome();startActivityAndWait();waitForHome()
        }
    }
    @Test fun timelineDatesAndAchievements() {
        prepareTestApp()
        rule.collect(packageName=PACKAGE,includeInStartupProfile=false) {
        startActivityAndWait();openDemo();turnTimeline();changeDateAndTabs()
        }
    }
}

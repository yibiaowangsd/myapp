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
    @Before fun prepare()=prepareTestApp()
    @Test fun startup()=rule.collect(packageName=PACKAGE,includeInStartupProfile=true) {
        pressHome();startActivityAndWait();waitForHome()
    }
    @Test fun timelineDatesAndAchievements()=rule.collect(packageName=PACKAGE,includeInStartupProfile=false) {
        startActivityAndWait();openDemo();turnTimeline();changeDateAndTabs()
    }
}

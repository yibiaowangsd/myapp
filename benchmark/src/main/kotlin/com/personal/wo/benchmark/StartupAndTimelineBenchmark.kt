package com.personal.wo.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupAndTimelineBenchmark {
    @get:Rule val rule=MacrobenchmarkRule()
    @Before fun prepare()=prepareTestApp()
    @Test fun coldStartup()=startup(StartupMode.COLD)
    @Test fun warmStartup()=startup(StartupMode.WARM)
    private fun startup(mode:StartupMode)=rule.measureRepeated(packageName=PACKAGE,
        metrics=listOf(StartupTimingMetric()),iterations=10,startupMode=mode,
        compilationMode=CompilationMode.Partial(),setupBlock={pressHome()}) {
        startActivityAndWait();waitForHome()
    }
    @Test fun timelineFrames()=rule.measureRepeated(packageName=PACKAGE,
        metrics=listOf(FrameTimingMetric()),iterations=5,compilationMode=CompilationMode.Partial(),
        setupBlock={startActivityAndWait();openDemo()}) { turnTimeline() }
    @Test fun datesAndTabs()=rule.measureRepeated(packageName=PACKAGE,
        metrics=listOf(FrameTimingMetric()),iterations=5,compilationMode=CompilationMode.Partial(),
        setupBlock={startActivityAndWait();openDemo()}) { changeDateAndTabs() }
}

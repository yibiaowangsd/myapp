package com.personal.wo

import android.Manifest
import com.personal.wo.collector.DirectPermissionPlan
import com.personal.wo.core.*
import org.junit.Assert.*
import org.junit.Test

class DirectPermissionPlanTest {
    @Test fun api31UsesHardwareActivityAndStorageWithoutHealthConnect() {
        val p=DirectPermissionPlan.ordinary(31,Source.entries.toSet(),Source.entries.toSet())
        assertTrue(Manifest.permission.ACTIVITY_RECOGNITION in p)
        assertTrue(Manifest.permission.READ_EXTERNAL_STORAGE in p)
        assertFalse(Manifest.permission.POST_NOTIFICATIONS in p)
        assertFalse(p.any{it.contains("health")})
    }
    @Test fun activityPermissionCanRevealAVendorSensorHiddenBeforeGrant() {
        val p=DirectPermissionPlan.ordinary(31,setOf(Source.STEPS,Source.LOCATION),emptySet())
        assertEquals(listOf(Manifest.permission.ACTIVITY_RECOGNITION),p)
        assertTrue(DirectPermissionPlan.ordinary(31,emptySet(),Source.entries.toSet()).isEmpty())
    }
    @Test fun specialPagesAreDeduplicatedAndDoNotLoopWhenGranted() {
        val enabled=Source.entries.toSet()
        val pending=DirectPermissionPlan.special(enabled,emptyList())
        assertEquals(listOf(Source.USAGE,Source.NOTIFICATION),pending)
        val granted=Source.entries.map{SourceState(it,authorization="GRANTED")}
        assertTrue(DirectPermissionPlan.special(enabled,granted).isEmpty())
    }
}

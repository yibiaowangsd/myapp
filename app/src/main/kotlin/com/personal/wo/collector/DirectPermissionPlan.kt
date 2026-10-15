package com.personal.wo.collector

import android.Manifest
import com.personal.wo.core.Source
import com.personal.wo.core.SourceState
import com.personal.wo.core.sourceReady

/** Ordinary permissions share one Android request. Special access pages occur once each. */
object DirectPermissionPlan {
    fun ordinary(sdk: Int, enabled: Set<Source>, supported: Set<Source>): List<String> = buildList {
        if(Source.STEPS in enabled) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if(Source.LOCATION in enabled && Source.LOCATION in supported) addAll(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        if(Source.PHOTO in enabled) addAll(when {
            sdk >= 34 -> listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            sdk >= 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        })
        if(Source.CALENDAR in enabled) add(Manifest.permission.READ_CALENDAR)
        if(sdk >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.distinct()

    fun special(enabled: Set<Source>, states: List<SourceState>): List<Source> = buildList {
        if(Source.USAGE in enabled && !sourceReady(states.firstOrNull { it.source == Source.USAGE })) add(Source.USAGE)
        if(enabled.any { it in setOf(Source.NOTIFICATION,Source.MEDIA) } && !sourceReady(states.firstOrNull { it.source == Source.NOTIFICATION })) add(Source.NOTIFICATION)
        // Background location and optional accessibility/Health Connect remain available
        // as direct system entries in Settings; don't send this Huawei to an absent service.
    }
}

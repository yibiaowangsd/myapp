package com.personal.wo.collector

import android.Manifest

/** An upgrade never changes an existing user's recording or permission choices. */
object PublicWelcomePlan {
    fun shouldShow(completed: Boolean, legacySetup: Boolean, startedMs: Long?): Boolean =
        !completed && !legacySetup && startedMs == null

    fun photoPermissions(sdk: Int): Array<String> = when {
        sdk >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        sdk >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

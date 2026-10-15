package com.personal.wo.collector

import com.personal.wo.core.Policy
import com.personal.wo.core.Source

/** Only sensor inputs affect the foreground service. Calendar/notification changes do not. */
data class RecordingPlan(val location:Boolean=false,val steps:Boolean=false,val precise:Boolean=false) {
    val needed:Boolean get()=location||steps
}

fun recordingPlan(p:Policy,locationAllowed:Boolean,locationEnabled:Boolean,stepsAvailable:Boolean,fineAllowed:Boolean):RecordingPlan {
    if(!p.requested||p.paused)return RecordingPlan()
    val location=Source.LOCATION in p.enabledSources&&locationAllowed&&locationEnabled
    return RecordingPlan(location,Source.STEPS in p.enabledSources&&stepsAvailable,location&&fineAllowed)
}

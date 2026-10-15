package com.personal.wo.collector

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize collectors and coalesce automatic resumes/workers, never suppress a manual refresh. */
internal class PullGate(private val clock:()->Long,private val cooldownMs:Long=30_000) {
    private val mutex=Mutex()
    private var completedAt:Long?=null
    private var completedInForeground=false
    suspend fun run(force:Boolean=false,foreground:Boolean=false,action:suspend()->Unit):Boolean=mutex.withLock {
        val now=clock()
        // A worker may have skipped health data that is only readable while visible.
        if(!force&&(!foreground||completedInForeground)&&completedAt?.let { now>=it&&now-it<cooldownMs }==true)return@withLock false
        action()
        completedAt=clock()
        completedInForeground=foreground
        true
    }
}

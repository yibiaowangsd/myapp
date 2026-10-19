package com.personal.wo.collector

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces ordinary resumes. Permission results and policy changes always re-probe. */
internal class ProbeGate(private val clock:()->Long,private val ttlMs:Long=30_000) {
    private val mutex=Mutex()
    private var completedAt:Long?=null
    private var generation:Long?=null
    suspend fun run(force:Boolean,policyGeneration:Long,action:suspend()->Unit):Boolean=mutex.withLock {
        val now=clock()
        if(!force&&generation==policyGeneration&&completedAt?.let { now>=it&&now-it<ttlMs }==true)return@withLock false
        action()
        generation=policyGeneration;completedAt=clock()
        true
    }
}

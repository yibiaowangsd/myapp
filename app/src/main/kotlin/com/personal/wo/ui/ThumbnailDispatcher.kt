package com.personal.wo.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/** Two bounded workers. Queued centre-card requests overtake neighbours; equal keys share a decode. */
internal object ThumbnailDispatcher {
    private class Gate(val mutex:Mutex=Mutex(),var users:Int=0)
    private val lock=Any()
    private val gates=mutableMapOf<String,Gate>()
    private val tasks=ConcurrentHashMap<String,Request>()
    private val sequence=AtomicLong()
    private val executor=ThreadPoolExecutor(2,2,10,TimeUnit.SECONDS,PriorityBlockingQueue<Runnable>()) { task ->
        Thread(task,"wo-thumbnail").apply { isDaemon=true }
    }.apply { allowCoreThreadTimeOut(true) }
    private class Request(@Volatile var priority:Int,val sequence:Long,val key:String,val work:()->Unit):Runnable,Comparable<Request> {
        override fun run()=work()
        override fun compareTo(other:Request)=compareValuesBy(this,other,{it.priority},{it.sequence})
    }
    suspend fun load(context:Context,uri:String,width:Int,height:Int,priority:Int,key:String):Bitmap? {
        val gate=synchronized(lock) { gates.getOrPut(key){Gate()}.also { it.users++ } }
        tasks[key]?.let { task ->
            if(priority<task.priority&&executor.remove(task)) { task.priority=priority;executor.execute(task) }
        }
        try {
            return gate.mutex.withLock {
                ThumbnailCache.cache.get(key)?.let { return@withLock it }
                suspendCancellableCoroutine<Bitmap?> { continuation ->
                    val signal=CancellationSignal()
                    lateinit var task:Request
                    task=Request(priority,sequence.getAndIncrement(),key) {
                        try {
                            if(!continuation.isActive)return@Request
                            val result=runCatching { context.contentResolver.loadThumbnail(Uri.parse(uri),Size(width,height),signal) }.getOrNull()
                            if(continuation.isActive) {
                                if(result!=null&&ThumbnailCache.key(uri,width,height)==key)ThumbnailCache.cache.put(key,result)
                                continuation.resume(result)
                            }
                        } finally { tasks.remove(key,task) }
                    }
                    tasks[key]=task
                    continuation.invokeOnCancellation { signal.cancel();executor.remove(task);tasks.remove(key,task) }
                    if(continuation.isActive)executor.execute(task) else tasks.remove(key,task)
                }
            }
        } finally { synchronized(lock) { gate.users--;if(gate.users==0)gates.remove(key) } }
    }
}

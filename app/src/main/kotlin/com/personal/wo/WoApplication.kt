package com.personal.wo

import android.app.Application
import android.content.Context
import com.personal.wo.ui.ThumbnailCache
import android.os.UserManager
import androidx.room.Room
import androidx.work.Configuration
import com.personal.wo.core.*
import com.personal.wo.data.*
import com.personal.wo.collector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.concurrent.atomic.AtomicLong

class WoApplication : Application(), Configuration.Provider {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var ready = CompletableDeferred<Graph>()
    private var initializing=false
    @Volatile var currentGraph: Graph? = null; private set
    val diagnostics by lazy { StartupDiagnostics(this) }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()
    override fun onCreate() { super.onCreate();diagnostics.installCrashRecorder();initialize() }
    @Synchronized fun initialize() {
        if (ready.isCompleted || initializing) return
        initializing=true
        val attempt=ready
        scope.launch {
            var candidate:Graph?=null
            val result=recoverableTask {
                check(getSystemService(UserManager::class.java)?.isUserUnlocked != false) { "设备尚未解锁" }
                Graph(this@WoApplication).also { candidate=it;it.repo.initialize() }
            }
            result.fold(onSuccess = {
                synchronized(this@WoApplication) { currentGraph=it;initializing=false;attempt.complete(it) }
            }, onFailure = {
                candidate?.let { g -> recoverableCall { g.close() } }
                diagnostics.record("数据库与本地组件初始化",it)
                synchronized(this@WoApplication) { initializing=false;attempt.completeExceptionally(it) }
            })
        }
    }
    suspend fun graph(): Graph { val attempt=synchronized(this) { initialize();ready };return attempt.await() }
    @Synchronized fun retryInitialization() {
        if (currentGraph==null && ready.isCompleted && !initializing) ready=CompletableDeferred()
        initialize()
    }
    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        ThumbnailCache.trim(level)
    }
    override fun onLowMemory() { super.onLowMemory();ThumbnailCache.cache.evictAll() }
    fun submit(observation: Observation) { currentGraph?.offer(observation) }
}

class Graph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val vault = KeyVault(context)
    val config = context.assets.open("default_config.json").bufferedReader().use { WoJson.decodeFromString<EngineConfig>(it.readText()).checked() }
    val db: WoDatabase
    val repo: WoRepository
    val collector: PullCollectors
    private data class Submission(val observation: Observation,val generation: Long)
    private val queue = Channel<Submission>(config.queueCapacity)
    private val dropped = AtomicLong(0)
    init {
        System.loadLibrary("sqlcipher")
        db = Room.databaseBuilder(context,WoDatabase::class.java,"wo.db").openHelperFactory(SupportOpenHelperFactory(vault.password())).setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).addCallback(DatabaseOpenCallback()).build()
        repo = WoRepository(db,vault,config,context)
        collector = PullCollectors(context,repo)
        scope.launch {
            while(isActive) {
                val first=queue.receive(); val batch=mutableListOf(first)
                val deadline=android.os.SystemClock.elapsedRealtime()+if(first.observation.source==Source.NOTIFICATION) 1500 else 4500
                while(batch.size<100 && android.os.SystemClock.elapsedRealtime()<deadline) {
                    val next=withTimeoutOrNull((deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)) { queue.receive() } ?: break
                    batch+=next
                }
                batch.groupBy { it.generation }.forEach { (g,os) ->
                    runCatching { repo.ingest(os.map { it.observation },g) }.onFailure { runCatching { repo.log("D001_INGEST_FAILED",os.size.toLong()) } }
                }
                val n=dropped.getAndSet(0); if(n>0) runCatching { repo.log("QUEUE_DROPPED",n) }
                WorkScheduling.dayBuild(context)
            }
        }
    }
    fun offer(o: Observation) {
        val p=repo.cachedPolicy
        if(!p.requested || p.paused)return
        if(queue.trySend(Submission(o,p.generation)).isFailure) dropped.incrementAndGet()
    }
    fun close() { scope.cancel();queue.cancel();db.close() }
}

fun Context.wo(): WoApplication = applicationContext as WoApplication

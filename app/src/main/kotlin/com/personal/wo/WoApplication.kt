package com.personal.wo

import android.app.Application
import android.content.Context
import android.os.UserManager
import androidx.room.Room
import com.personal.wo.core.*
import com.personal.wo.data.*
import com.personal.wo.collector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.concurrent.atomic.AtomicLong

class WoApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Graph>()
    private var initializing=false
    @Volatile var currentGraph: Graph? = null; private set
    override fun onCreate() { super.onCreate(); initialize() }
    @Synchronized fun initialize() {
        if (!getSystemService(UserManager::class.java).isUserUnlocked || ready.isCompleted || initializing) return
        initializing=true
        scope.launch {
            synchronized(this@WoApplication) { if (currentGraph != null) return@launch }
            runCatching { Graph(this@WoApplication).also { it.repo.initialize() } }.fold(
                onSuccess = { currentGraph = it; ready.complete(it) }, onFailure = { ready.completeExceptionally(it) })
        }
    }
    suspend fun graph(): Graph { initialize(); return ready.await() }
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
        db = Room.databaseBuilder(context,WoDatabase::class.java,"wo.db").openHelperFactory(SupportOpenHelperFactory(vault.password())).setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).addCallback(object:androidx.room.RoomDatabase.Callback(){override fun onOpen(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("PRAGMA busy_timeout=5000")}}).build()
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
}

fun Context.wo(): WoApplication = applicationContext as WoApplication

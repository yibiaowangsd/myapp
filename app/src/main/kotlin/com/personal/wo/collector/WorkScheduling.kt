package com.personal.wo.collector

import android.content.*
import android.app.NotificationManager
import androidx.work.*
import com.personal.wo.wo
import com.personal.wo.MainActivity
import com.personal.wo.recoverableCall
import com.personal.wo.recoverableTask
import com.personal.wo.core.*
import kotlinx.coroutines.*
import java.time.*
import java.util.concurrent.TimeUnit

object WorkScheduling {
    private fun guarded(c:Context,action:()->Unit):Boolean = recoverableCall(action)
        .onFailure { c.wo().diagnostics.record("后台调度",it) }.isSuccess
    fun schedule(c:Context) = guarded(c) {
        val w=WorkManager.getInstance(c)
        w.enqueueUniquePeriodicWork("wo.incremental",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<IncrementalPullWorker>(30,TimeUnit.MINUTES).addTag("wo.recording").build())
        w.enqueueUniquePeriodicWork("wo.maintenance",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<MaintenanceWorker>(1,TimeUnit.DAYS).setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build())
    }
    fun dayBuild(c:Context) = guarded(c) { WorkManager.getInstance(c).enqueueUniqueWork("wo.day-build",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<DayBuildWorker>().setInitialDelay(5,TimeUnit.MINUTES).addTag("wo.recording").build()) }
    fun mediaPull(c:Context) = guarded(c) { WorkManager.getInstance(c).enqueueUniqueWork("wo.media-index",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<IncrementalPullWorker>().setInitialDelay(1,TimeUnit.MINUTES).addTag("wo.recording").build()) }
    fun pause(c:Context) = guarded(c) { WorkManager.getInstance(c).cancelAllWorkByTag("wo.recording") }
}
class IncrementalPullWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p) {
    override suspend fun doWork():Result { return try {
        val g=applicationContext.wo().graph();if(!g.repo.cachedPolicy.requested||g.repo.cachedPolicy.paused)return Result.success()
        withTimeout(20_000) { g.collector.probe();g.collector.pull();WorkScheduling.dayBuild(applicationContext) };Result.success()
    } catch(e:TimeoutCancellationException) { Result.success() } catch(e:CancellationException) { throw e } catch(e:Exception) { if(runAttemptCount<3)Result.retry() else Result.failure() } }
}
class DayBuildWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p) {
    override suspend fun doWork():Result { return try {
        val r=applicationContext.wo().graph().repo
        if(!r.cachedPolicy.requested||r.cachedPolicy.paused)return Result.success()
        val dirty=r.db.dao().dirty();val today=LocalDate.now(ZoneId.of(r.cachedPolicy.archiveZone))
        (r.dirtyDays()+today+today.minusDays(1)).sortedDescending().take(16).forEach { r.currentDay(it) };r.clearDirty(dirty.map { it.id });Result.success()
    } catch(e:CancellationException) { throw e } catch(e:Exception) { if(runAttemptCount<3)Result.retry() else Result.failure() } }
}
class MaintenanceWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p) {
    override suspend fun doWork():Result { applicationContext.wo().graph().repo.maintenance();return Result.success() }
}
class RecoveryReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,intent:Intent) {
        val pending=goAsync()
        c.wo().scope.launch { try { recoverableTask {
            c.wo().initialize();val g=c.wo().graph();val p=g.repo.cachedPolicy
            if(intent.action in listOf(Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED)) {
                g.repo.log("CLOCK_SEGMENT_CHANGED")
                // End known interactive activity at a system clock boundary instead of creating a negative duration.
                val now=System.currentTimeMillis();val key=g.vault.sourceKey("clock",now)
                g.offer(Observation(stableId("clock",key),Source.USAGE,key,Kind.SHUTDOWN,now,quality=listOf("CLOCK_CONFLICT")))
            }
            if(p.requested&&!p.paused) {
                WorkScheduling.schedule(c)
                if(p.lastAliveMs>0)g.repo.coverage(Source.LOCATION,p.lastAliveMs,System.currentTimeMillis(),false,"进程或设备中断，位置与消失通知无法补回")
                // A tap is a reliable legal entry on every supported Android version.
                RecordingService.recoveryNotification(c)
            }
        }.onFailure { c.wo().diagnostics.record("系统恢复入口",it) } } finally { pending.finish() } }
    }
}
class PauseReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,intent:Intent) {
        val result=goAsync();c.wo().scope.launch { try { recoverableTask { c.wo().graph().repo.command("pause");c.stopService(Intent(c,RecordingService::class.java));WorkScheduling.pause(c) }.onFailure { c.wo().diagnostics.record("暂停入口",it) } } finally { result.finish() } }
    }
}

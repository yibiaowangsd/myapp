package com.personal.wo.collector

import android.content.Context
import androidx.work.*
import com.personal.wo.wo
import com.personal.wo.data.SharedSyncException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

object SharedDataWork {
    private fun constraints()=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
    fun schedule(context:Context,enabled:Boolean) {
        val manager=WorkManager.getInstance(context)
        if(!enabled) { manager.cancelUniqueWork("wo.shared-data");manager.cancelUniqueWork("wo.shared-now");return }
        manager.enqueueUniquePeriodicWork("wo.shared-data",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SharedDataWorker>(30,TimeUnit.MINUTES).setConstraints(constraints()).build())
    }
    fun soon(context:Context) {
        if(context.wo().currentGraph?.sharedData?.config()?.enabled!=true)return
        WorkManager.getInstance(context).enqueueUniqueWork("wo.shared-now",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SharedDataWorker>().setInitialDelay(20,TimeUnit.SECONDS).setConstraints(constraints()).build())
    }
}
class SharedDataWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result=try {
        val service=applicationContext.wo().graph().sharedData
        val pending=if(service.config().enabled)service.sync().pending else 0
        if(pending>0&&runAttemptCount<3)Result.retry() else if(pending>0)Result.failure() else Result.success()
    } catch(e:CancellationException) { throw e }
    catch(e:SharedSyncException) { if(e.retryable&&runAttemptCount<3)Result.retry() else Result.failure() }
    catch(e:Exception) { if(runAttemptCount<3)Result.retry() else Result.failure() }
}

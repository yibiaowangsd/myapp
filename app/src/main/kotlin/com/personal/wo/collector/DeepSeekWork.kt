package com.personal.wo.collector

import android.content.Context
import androidx.work.*
import com.personal.wo.wo
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

object DeepSeekWork {
    fun schedule(context:Context,enabled:Boolean) {
        val manager=WorkManager.getInstance(context)
        if(!enabled) { manager.cancelUniqueWork("wo.deepseek-review");return }
        manager.enqueueUniquePeriodicWork("wo.deepseek-review",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeepSeekWorker>(12,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build())
    }
}
class DeepSeekWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result=try {
        val service=applicationContext.wo().graph().deepSeek
        if(service.config().enabled&&service.config().autoReview)service.review(automatic=true)
        Result.success()
    } catch(e:CancellationException) { throw e }
    catch(e:Exception) { Result.failure() }
}

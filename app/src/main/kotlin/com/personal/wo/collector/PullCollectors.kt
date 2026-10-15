package com.personal.wo.collector

import android.Manifest
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.os.Bundle
import android.os.Build
import android.os.UserManager
import android.provider.CalendarContract
import android.provider.MediaStore
import android.database.Cursor
import com.personal.wo.core.*
import com.personal.wo.data.WoRepository
import kotlinx.coroutines.*
import java.time.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.permission.HealthPermission
import kotlin.reflect.KClass

class PullCollectors(private val c:Context,private val repo:WoRepository) {
    private val labels=mutableMapOf<String,String>()
    fun label(pkg:String):String = labels.getOrPut(pkg) { runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg) }.also { if(labels.size>200) labels.clear() }
    suspend fun probe() { Capabilities.probe(c,repo.cachedPolicy).forEach { fresh -> val old=repo.source(fresh.source); repo.state(fresh.copy(lastSuccessMs=old.lastSuccessMs,cursor=old.cursor,freshness=old.freshness)) } }
    suspend fun pull(foreground:Boolean=false) {
        if(!repo.cachedPolicy.requested||repo.cachedPolicy.paused)return
        if(!c.getSystemService(UserManager::class.java).isUserUnlocked)return
        val actions=listOf(Source.USAGE to suspend { usage() },Source.PHOTO to suspend { photos() },Source.CALENDAR to suspend { calendar() },Source.HEALTH to suspend { health(foreground) })
        actions.forEach { (source,action) ->
            if(source !in repo.cachedPolicy.enabledSources)return@forEach
            try { action() } catch(e:CancellationException) { throw e } catch(e:SecurityException) { repo.state(repo.source(source).copy(authorization="DENIED",runtime="STOPPED",detail="P001:权限已撤回")) }
            catch(e:Exception) { repo.state(repo.source(source).copy(runtime="ERROR",detail="S001:来源暂时不可用（${e.javaClass.simpleName}）")); repo.log("SOURCE_${source.name}_FAILED") }
        }
        repo.heartbeat()
    }
    suspend fun usage() {
        if(!Capabilities.usage(c))return
        val p=repo.cachedPolicy; val now=System.currentTimeMillis(); val manager=c.getSystemService(UsageStatsManager::class.java)
        val today=Instant.ofEpochMilli(now).atZone(ZoneId.of(p.archiveZone)).toLocalDate()
        var from=maxOf((repo.source(Source.USAGE).cursor.toLongOrNull() ?: window(today,ZoneId.of(p.archiveZone)).start-6*3_600_000L)-300_000,0)
        while(from<now) {
            currentCoroutineContext().ensureActive()
            val end=minOf(now,from+6*3_600_000L); val events=manager.queryEvents(from,end); val batch=mutableListOf<Observation>()
            val event=UsageEvents.Event()
            while(events.hasNextEvent()) {
                events.getNextEvent(event)
                val kind=when(event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED->Kind.RESUMED
                    UsageEvents.Event.ACTIVITY_PAUSED->Kind.PAUSED
                    UsageEvents.Event.ACTIVITY_STOPPED->Kind.STOPPED
                    UsageEvents.Event.SCREEN_INTERACTIVE->Kind.SCREEN_ON
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE->Kind.SCREEN_OFF
                    UsageEvents.Event.KEYGUARD_SHOWN->Kind.LOCK
                    UsageEvents.Event.KEYGUARD_HIDDEN->Kind.UNLOCK
                    UsageEvents.Event.DEVICE_STARTUP->Kind.BOOT
                    UsageEvents.Event.DEVICE_SHUTDOWN->Kind.SHUTDOWN
                    else->null
                } ?: continue
                val pkg=event.packageName.orEmpty(); val cls=event.className.orEmpty(); val key=repo.vault.sourceKey("usage",event.timeStamp,pkg,cls,kind)
                batch+=Observation(stableId(Source.USAGE,key),Source.USAGE,key,kind,event.timeStamp,receivedMs=now,eventZoneId=ZoneId.systemDefault().id,payload=Payload(packageName=pkg,className=cls,label=if(pkg.isNotBlank()) label(pkg) else "屏幕事件"))
                if(batch.size==100) { repo.ingest(batch.toList(),p.generation); batch.clear() }
            }
            repo.ingest(batch,p.generation,Source.USAGE,end.toString(),Span(from,end)); from=end
        }
    }
    private fun Cursor.long(name:String)=getColumnIndex(name).let { if(it<0||isNull(it)) 0L else getLong(it) }
    private fun Cursor.string(name:String)=getColumnIndex(name).let { if(it<0||isNull(it)) "" else getString(it).orEmpty() }
    suspend fun photos(since:Long?=null) {
        val status=repo.source(Source.PHOTO); if(status.authorization !in listOf("GRANTED","PARTIAL"))return
        val p=repo.cachedPolicy; val now=System.currentTimeMillis(); val cursor=status.cursor.split(":")
        var mod=since?.div(1000) ?: cursor.firstOrNull()?.toLongOrNull() ?: (now-7*86_400_000L)/1000; var lastId=if(since!=null) 0L else cursor.getOrNull(1)?.toLongOrNull() ?: 0L
        repeat(3) {
            val uri=MediaStore.Files.getContentUri("external")
            val args=Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"(${MediaStore.Files.FileColumns.MEDIA_TYPE}=1 OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=3) AND (${MediaStore.MediaColumns.DATE_MODIFIED}>? OR (${MediaStore.MediaColumns.DATE_MODIFIED}=? AND ${MediaStore.MediaColumns._ID}>?))")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,arrayOf(mod.toString(),mod.toString(),lastId.toString()))
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,arrayOf(MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns._ID))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,ContentResolver.QUERY_SORT_DIRECTION_ASCENDING); putInt(ContentResolver.QUERY_ARG_LIMIT,200)
            }
            val batch=mutableListOf<Observation>()
            c.contentResolver.query(uri,arrayOf("_id","datetaken","date_added","date_modified","media_type"),args,null)?.use { q ->
                while(q.moveToNext()&&batch.size<200) {
                    val id=q.long("_id"); val dateTaken=q.long("datetaken"); val added=q.long("date_added")*1000
                    val captured=dateTaken>0&&dateTaken<now+300_000
                    val t=if(captured) dateTaken else added
                    mod=q.long("date_modified"); lastId=id
                    val native="${MediaStore.getVersion(c)}:$id"; val key=repo.vault.sourceKey("photo",native)
                    batch+=Observation(stableId(Source.PHOTO,key),Source.PHOTO,key,Kind.PHOTO,t,receivedMs=now,payload=Payload(uri=ContentUris.withAppendedId(uri,id).toString(),captured=captured,category=if(q.long("media_type")==3L) "VIDEO" else "IMAGE"))
                }
            }
            repo.ingest(batch,p.generation,Source.PHOTO,"$mod:$lastId")
            if(batch.size<200)return
        }
    }
    suspend fun calendar() {
        if(!Capabilities.granted(c,Manifest.permission.READ_CALENDAR))return
        val p=repo.cachedPolicy; val now=System.currentTimeMillis(); val from=now-7*86_400_000L; val end=now+7*86_400_000L
        val uri=CalendarContract.Instances.CONTENT_URI.buildUpon().apply { ContentUris.appendId(this,from);ContentUris.appendId(this,end) }.build()
        val batch=mutableListOf<Observation>(); var complete=true
        c.contentResolver.query(uri,arrayOf(CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.TITLE,CalendarContract.Instances.ALL_DAY,CalendarContract.Instances.STATUS),null,null,CalendarContract.Instances.BEGIN+" ASC")?.use { q ->
            while(q.moveToNext()) {
                if(batch.size>=500) { complete=false;break }
                if(q.long(CalendarContract.Instances.STATUS)==CalendarContract.Events.STATUS_CANCELED.toLong())continue
                var start=q.long(CalendarContract.Instances.BEGIN); var finish=q.long(CalendarContract.Instances.END)
                val allDay=q.long(CalendarContract.Instances.ALL_DAY)==1L
                if(allDay) {
                    // CalendarContract all-day times encode UTC dates, not an instant in the user's zone.
                    val zone=ZoneId.of(p.archiveZone)
                    start=Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                    finish=Instant.ofEpochMilli(finish).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                }
                if(finish<start)continue
                val key=repo.vault.sourceKey("calendar",q.long(CalendarContract.Instances.EVENT_ID),start)
                batch+=Observation(stableId(Source.CALENDAR,key),Source.CALENDAR,key,Kind.CALENDAR,start,finish,now,payload=Payload(label=q.string(CalendarContract.Instances.TITLE).take(256),allDay=allDay))
            }
        } ?: return
        repo.ingest(batch,p.generation,Source.CALENDAR,now.toString(),replaceSpan=if(complete) Span(from,end) else null)
    }
    suspend fun health(foreground:Boolean) {
        if(HealthConnectClient.getSdkStatus(c)!=HealthConnectClient.SDK_AVAILABLE)return
        val client=HealthConnectClient.getOrCreate(c); val permissions=client.permissionController.getGrantedPermissions()
        if(!foreground && (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)!=HealthConnectFeatures.FEATURE_STATUS_AVAILABLE || HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in permissions))return
        val p=repo.cachedPolicy; val now=System.currentTimeMillis(); val zone=ZoneId.of(p.archiveZone); val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val batch=mutableListOf<Observation>(); val from=window(today.minusDays(6),zone).start; val until=window(today,zone).end
        if(HealthPermission.getReadPermission(StepsRecord::class) in permissions) {
            repeat(7) { i -> val w=window(today.minusDays(i.toLong()),zone)
                val aggregate=client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL),TimeRangeFilter.between(Instant.ofEpochMilli(w.start),Instant.ofEpochMilli(minOf(w.end,now)))))
                val count=aggregate[StepsRecord.COUNT_TOTAL]
                if(count!=null) { val key=repo.vault.sourceKey("health.steps",w.start,zone.id); batch+=Observation(stableId(Source.HEALTH,key),Source.HEALTH,key,Kind.HEALTH_STEPS,w.start,w.end,now,payload=Payload(steps=count,label="Health Connect聚合")) }
            }
        }
        var complete=true
        if(HealthPermission.getReadPermission(SleepSessionRecord::class) in permissions) {
            var token:String?=null
            do {
                val result=client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,TimeRangeFilter.between(Instant.ofEpochMilli(from),Instant.ofEpochMilli(until)),pageSize=200,pageToken=token))
                result.records.forEach { record -> val key=repo.vault.sourceKey("health.sleep",record.metadata.id)
                    batch+=Observation(stableId(Source.HEALTH,key),Source.HEALTH,key,Kind.SLEEP,record.startTime.toEpochMilli(),record.endTime.toEpochMilli(),now,payload=Payload(label=record.metadata.dataOrigin.packageName)) }
                token=result.pageToken
            } while(token!=null && batch.size<450)
            if(token!=null)complete=false
        }
        if(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in permissions && batch.size<450) {
            val result=client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,TimeRangeFilter.between(Instant.ofEpochMilli(from),Instant.ofEpochMilli(until)),pageSize=200))
            result.records.forEach { record -> val key=repo.vault.sourceKey("health.exercise",record.metadata.id)
                batch+=Observation(stableId(Source.HEALTH,key),Source.HEALTH,key,Kind.EXERCISE,record.startTime.toEpochMilli(),record.endTime.toEpochMilli(),now,payload=Payload(label=record.title ?: record.metadata.dataOrigin.packageName)) }
            if(result.pageToken!=null)complete=false
        }
        // Reconcile bounded snapshots including deleted provider records. Never replace a truncated snapshot.
        repo.ingest(batch.take(512),p.generation,Source.HEALTH,now.toString(),replaceSpan=if(complete&&batch.size<=512) Span(from,until) else null)
    }
}

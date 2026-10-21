package com.personal.wo

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import com.personal.wo.collector.PullCollectors
import com.personal.wo.core.*
import com.personal.wo.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PhotoHistoryImportTest {
    private lateinit var db:WoDatabase
    private lateinit var repo:WoRepository
    private lateinit var collector:PullCollectors
    private lateinit var provider:Photos
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.now(zone).minusDays(45)
    @Before fun open()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        db=Room.inMemoryDatabaseBuilder(context,WoDatabase::class.java).allowMainThreadQueries().build()
        db.dao().putPolicy(PolicyRow(json=WoJson.encodeToString(Policy(archiveZone=zone.id,requested=true,enabledSources=setOf(Source.PHOTO)))))
        repo=WoRepository(db,KeyVault(context),EngineConfig(),context)
        repo.initialize();repo.state(SourceState(Source.PHOTO,authorization="GRANTED"))
        provider=Photos(window(date,zone).start)
        provider.attachInfo(context,ProviderInfo().apply { authority="media" })
        ShadowContentResolver.registerProviderInternal("media",provider)
        // Robolectric tests the media/index pipeline; device-bound HMAC remains the production default.
        collector=PullCollectors(context,repo,photoSourceKey={native->stableId("test.photo",native)})
    }
    @After fun close(){db.close()}
    @Test fun importContinuesPastTheOldSixHundredItemLimitAndBuildsOldDays()=runBlocking {
        val progress=mutableListOf<Int>()
        assertEquals(650,collector.importPhotoHistory(365){count,_->progress+=count})
        assertEquals(listOf(200,400,600,650),progress)
        assertEquals(650,repo.currentDay(date).episodes.size)
        assertEquals(50,repo.search("",date,all=true,filter="PHOTO").size)
        // History scanning must not replace the independent incremental sync cursor.
        assertEquals("",repo.source(Source.PHOTO).cursor)
    }
    @Test fun pausedOrDeletedRecordsAreNotReimported()=runBlocking {
        collector.importPhotoHistory(365){_,_->}
        val first=repo.currentDay(date).episodes.first()
        repo.delete(repo.preview(first.startMs,first.startMs+1,first.id))
        assertEquals(649,collector.importPhotoHistory(365){_,_->})
        assertEquals(649,repo.currentDay(date).episodes.size)
        repo.command("pause")
        try { collector.importPhotoHistory(365){_,_->};fail("Paused recording must stay paused") } catch(_:IllegalStateException) {}
        assertTrue(repo.cachedPolicy.paused)
    }
    private class Photos(private val start:Long):ContentProvider() {
        override fun onCreate()=true
        override fun call(method:String,arg:String?,extras:Bundle?)=Bundle().apply { putString(android.content.Intent.EXTRA_TEXT,"test-media-version") }
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
            val after=selectionArgs?.firstOrNull()?.toInt() ?: 0
            val names=projection?.map { it }.orEmpty().ifEmpty { listOf("_id","datetaken","date_added","date_modified","media_type") }
            return MatrixCursor(names.toTypedArray()).apply {
                for(id in (after+1)..minOf(after+200,650)) {
                    val time=start+id*60_000L
                    addRow(names.map { column->when(column){"_id"->id.toLong();"datetaken"->time;"date_added","date_modified"->time/1000;"media_type"->1L;else->0L} }.toTypedArray())
                }
            }
        }
        override fun getType(uri:Uri)="image/jpeg"
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    }
}

package com.personal.wo

import android.app.Application
import androidx.room.Room
import com.personal.wo.core.*
import com.personal.wo.data.*
import com.personal.wo.ui.ThumbnailCache
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class DayCacheTest {
    private lateinit var db:WoDatabase
    private lateinit var repo:WoRepository
    private val queries=CopyOnWriteArrayList<String>()
    private val date=LocalDate.of(2026,10,15)
    private val policy=Policy(archiveZone="Asia/Shanghai",requested=true)
    @Before fun open()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        db=Room.inMemoryDatabaseBuilder(context,WoDatabase::class.java).allowMainThreadQueries()
            .setQueryCallback({sql,_->queries.add(sql)},Executor{it.run()}).build()
        db.dao().putPolicy(PolicyRow(json=WoJson.encodeToString(policy)))
        repo=WoRepository(db,KeyVault(context),EngineConfig(),context)
        repo.initialize()
        val start=window(date,ZoneId.of(policy.archiveZone)).start
        repo.ingest(listOf(Observation("photo",Source.PHOTO,"photo",Kind.PHOTO,start+3_600_000,
            payload=Payload(uri="content://test/1",captured=true))),policy.generation)
    }
    @After fun close(){db.close();ThumbnailCache.cache.evictAll()}

    @Test fun unchangedDateReturnsSameRevisionWithoutReadingRawObservationsAgain()=runBlocking {
        val first=repo.currentDay(date);queries.clear()
        val second=repo.currentDay(date)
        assertSame(first,second)
        assertTrue(queries.none{it.contains("FROM observation",true)})
        assertTrue(queries.none{it.contains("daily_summary",true)})
    }
    @Test fun persistedSnapshotDoesNotRebuildEvenWhenThereAreUnchangedExclusions()=runBlocking {
        repo.configure { it.copy(exclusions=listOf(Exclusion("unrelated",0,1,reason="PAUSE"))) }
        val built=repo.currentDay(date);repo.initialize();queries.clear()
        assertEquals(built,repo.cachedDay(date))
        assertTrue(queries.none{it.contains("FROM observation",true)})
    }
    @Test fun policyChangeInvalidatesSavedSnapshotAndDoesNotShowExcludedPhotos()=runBlocking {
        val first=repo.currentDay(date);assertEquals(1,first.episodes.size)
        val w=window(date,ZoneId.of(policy.archiveZone))
        repo.configure { it.copy(exclusions=listOf(Exclusion("exclude",w.start,w.end,reason="USER_RANGE"))) }
        assertNull(repo.cachedDay(date))
        assertTrue(repo.currentDay(date).episodes.isEmpty())
    }
    @Test fun newDataAndDeletionInvalidateMemoryCache()=runBlocking {
        val first=repo.currentDay(date)
        val start=window(date,ZoneId.of(policy.archiveZone)).start
        repo.ingest(listOf(Observation("next",Source.PHOTO,"next",Kind.PHOTO,start+7_200_000,
            payload=Payload(uri="content://test/2",captured=true))),policy.generation)
        val next=repo.currentDay(date)
        assertTrue(next.revision>first.revision);assertEquals(2,next.episodes.size)
        repo.delete(repo.preview(start,start+86_400_000))
        assertNull(repo.cachedDay(date));assertTrue(repo.currentDay(date).episodes.isEmpty())
    }
    @Test fun unrelatedDatesAndDisplayOnlySourceChangesKeepTheCachedDay()=runBlocking {
        val first=repo.currentDay(date)
        val later=window(date.plusDays(8),ZoneId.of(policy.archiveZone)).start
        repo.ingest(listOf(Observation("later",Source.PHOTO,"later",Kind.PHOTO,later+1,
            payload=Payload(uri="content://test/later",captured=true))),policy.generation)
        repo.state(SourceState(Source.HEALTH,authorization="UNKNOWN",runtime="STOPPED"))
        queries.clear()
        assertSame(first,repo.currentDay(date))
        assertTrue(queries.none{it.contains("FROM observation",true)})
    }
    @Test fun identicalEvidenceWithANewArrivalTimestampDoesNotRebuildOrRewriteIt()=runBlocking {
        val first=repo.currentDay(date)
        val start=window(date,ZoneId.of(policy.archiveZone)).start
        repo.ingest(listOf(Observation("photo",Source.PHOTO,"photo",Kind.PHOTO,start+3_600_000,
            receivedMs=start+10_000_000,payload=Payload(uri="content://test/1",captured=true))),policy.generation)
        queries.clear()
        assertSame(first,repo.currentDay(date))
        assertTrue(queries.none{it.contains("FROM observation",true)})
    }
    @Test fun restartReusesACurrentSnapshotWithoutDecodingRawHistory()=runBlocking {
        val first=repo.currentDay(date)
        repo.initialize();queries.clear()
        assertEquals(first,repo.currentDay(date))
        assertTrue(queries.none{it.contains("FROM observation",true)})
    }
    @Test fun outdatedPermissionProbesCannotRestoreAnOldRecordingState()=runBlocking {
        repo.configure{it.copy(paused=true)}
        val before=repo.sources()
        repo.probed(listOf(SourceState(Source.USAGE,authorization="GRANTED",runtime="RUNNING")),policy.generation)
        assertEquals(before,repo.sources())
    }
    @Test fun thumbnailInvalidationIsScopedByUriAndSize() {
        val a=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        val first=ThumbnailCache.key("content://test/a",128,128)
        val other=ThumbnailCache.key("content://test/b",512,256)
        ThumbnailCache.cache.put(first,a);ThumbnailCache.cache.put(other,b)
        ThumbnailCache.invalidate(listOf("content://test/a"))
        assertNull(ThumbnailCache.cache.get(first))
        assertSame(b,ThumbnailCache.cache.get(other))
        assertNotEquals(first,ThumbnailCache.key("content://test/a",128,128))
        assertNotEquals(other,ThumbnailCache.key("content://test/b",256,256))
    }
    @Test fun liveCacheExpiresAndBoundsMemoryAcrossDatesAndPolicies() {
        val cache=RecentDayCache(2);val day=demoDay(date,"Asia/Shanghai")
        cache.put("one",day,1,1,100)
        assertNotNull(cache.get("one",1,1,29_999,true))
        assertNull(cache.get("one",1,1,30_100,true))
        assertNotNull(cache.get("one",1,1,30_100,false))
        assertNull(cache.get("one",2,1,200,false))
        assertNull(cache.get("one",1,2,200,false))
        assertNull(cache.get("one",1,1,50,false))
        cache.put("two",day,1,1,100);cache.put("three",day,1,1,100)
        assertNull(cache.get("one",1,1,200,false))
        cache.clear();assertNull(cache.get("three",1,1,200,false))
        cache.put("live",day,1,1,day.windowEndMs-100)
        assertNull(cache.get("live",1,1,day.windowEndMs+1,false))
    }
    @Test fun briefBackgroundingKeepsThumbnailsButCriticalMemoryPressureClearsThem() {
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        ThumbnailCache.cache.put("content://test/1",bitmap)
        ThumbnailCache.trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertSame(bitmap,ThumbnailCache.cache.get("content://test/1"))
        ThumbnailCache.trim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertNull(ThumbnailCache.cache.get("content://test/1"))
    }
}

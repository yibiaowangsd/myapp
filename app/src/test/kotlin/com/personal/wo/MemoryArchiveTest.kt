package com.personal.wo

import android.app.Application
import androidx.room.Room
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MemoryArchiveTest {
    private lateinit var db:WoDatabase
    private lateinit var repo:WoRepository
    private val zone=ZoneId.of("Asia/Shanghai")
    private val date=LocalDate.of(2024,1,12)
    @Before fun open()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        db=Room.inMemoryDatabaseBuilder(context,WoDatabase::class.java).allowMainThreadQueries().build()
        db.dao().putPolicy(PolicyRow(json=WoJson.encodeToString(Policy(archiveZone=zone.id,requested=true))))
        repo=WoRepository(db,KeyVault(context),EngineConfig(),context)
        repo.initialize()
        val start=window(date,zone).start
        repo.ingest((0 until 61).map { i -> Observation("photo-$i",Source.PHOTO,"photo-$i",Kind.PHOTO,start+i*60_000,
            payload=Payload(uri="content://test/$i",captured=true)) },repo.cachedPolicy.generation)
        repo.currentDay(date)
        Unit
    }
    @After fun close(){db.close()}
    @Test fun emptySearchReturnsRecentRecordsWithStableNonOverlappingPages()=runBlocking {
        val first=repo.search("",date,all=true)
        val second=repo.search("",date,all=true,offset=50)
        assertEquals(50,first.size);assertEquals(11,second.size)
        assertEquals(61,(first+second).map { it.id }.distinct().size)
        assertTrue(first.first().startMs>second.last().startMs)
    }
    @Test fun photosAndFavoritesFilterTheWholeArchiveBeforePagination()=runBlocking {
        val oldest=repo.search("",date,all=true,offset=50).last()
        repo.annotate(oldest,"海边的100%_日落",true,true)
        assertEquals(oldest.id,repo.search("",date,all=true,filter="FAVORITE").single().id)
        assertEquals(oldest.id,repo.search("100%_",date,all=true).single().id)
        assertEquals(oldest.id,repo.search("海边",date,all=true).single().id)
        assertTrue(repo.search("",date,all=true,filter="APP").isEmpty())
    }
    @Test fun dateQueryCanFindOldMemoriesOutsideTheRecentWindow()=runBlocking {
        val results=repo.search("2024-01-12",LocalDate.now().minusDays(90),all=false)
        assertEquals(50,results.size)
        assertTrue(repo.search("2024-01-13",date,all=true).isEmpty())
    }
    @Test fun photoMemoriesSurviveRawCleanupAndTheTwoYearEpisodeWindow()=runBlocking {
        repo.maintenance()
        assertEquals(50,repo.search("",date,all=true).size)
        val restored=repo.currentDay(date)
        assertEquals(61,restored.episodes.size)
        assertTrue(restored.episodes.all { it.evidence.all { evidence->!evidence.rawAvailable } })
        val deleted=restored.episodes.first()
        repo.delete(repo.preview(deleted.startMs,deleted.startMs+1,deleted.id))
        assertEquals(60,repo.currentDay(date).episodes.size)
        assertNull(repo.episode(deleted.id))
    }
}

package com.personal.wo

import android.app.Application
import androidx.room.Room
import com.personal.wo.core.*
import com.personal.wo.data.*
import com.personal.wo.health.*
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
class HuaweiHealthRepositoryTest {
    private lateinit var db:WoDatabase
    private lateinit var repo:WoRepository
    private lateinit var sink:HuaweiRepositorySink
    private val zone=ZoneId.of("Asia/Hong_Kong")
    private val date=LocalDate.now(zone).minusDays(1)
    private val day=window(date,zone)
    @Before fun open()=runBlocking {
        val c=RuntimeEnvironment.getApplication()
        db=Room.inMemoryDatabaseBuilder(c,WoDatabase::class.java).allowMainThreadQueries().build()
        db.dao().putPolicy(PolicyRow(json=WoJson.encodeToString(Policy(requested=true,archiveZone=zone.id))))
        repo=WoRepository(db,KeyVault(c),EngineConfig(),c);repo.initialize()
        // Room/identity tests only: Robolectric does not implement AndroidKeyStore.
        // Production continues to use the existing device-bound HMAC source key.
        sink=HuaweiRepositorySink(repo) { account,archiveZone,kind,id->stableId("test.identity",account,archiveZone,kind,id) }
    }
    @After fun close(){db.close()}
    private suspend fun huawei(count:Long,account:String="account-A") {
        sink.save(account,HuaweiScope.STEPS,day,HuaweiPage(listOf(HuaweiRecord("daily:$date",Kind.HEALTH_STEPS,day.start,day.end,count,"华为运动健康")),true),zone,System.currentTimeMillis(),repo.cachedPolicy.generation)
    }
    private suspend fun otherSources() {
        repo.ingest(listOf(
            Observation("hc",Source.HEALTH,"hc",Kind.HEALTH_STEPS,day.start,day.end,payload=Payload(steps=300,label="Health Connect聚合")),
            Observation("phone",Source.STEPS,"phone",Kind.STEP_DELTA,day.start+100,day.start+200,payload=Payload(steps=10))
        ),repo.cachedPolicy.generation)
    }
    @Test fun snapshotsStayProviderAndKindIsolatedAndRepeatedSyncIsIdempotent()=runBlocking {
        otherSources();huawei(500);huawei(400)
        assertEquals(3,db.dao().observationCount(day.start,day.end))
        assertEquals(400L,repo.currentDay(date).metrics.first { it.key=="steps" }.value)
        repo.ingest(emptyList(),repo.cachedPolicy.generation,Source.HEALTH,replaceSpan=day,replaceHealthProvider=HEALTH_CONNECT)
        assertEquals(2,db.dao().observationCount(day.start,day.end))
        assertEquals(400L,repo.currentDay(date).metrics.first { it.key=="steps" }.value)
        val sleep=HuaweiRecord("sleep",Kind.SLEEP,day.start+1000,day.start+2000,label="华为运动健康")
        sink.save("account-A",HuaweiScope.SLEEP,day,HuaweiPage(listOf(sleep)),zone,System.currentTimeMillis(),repo.cachedPolicy.generation)
        huawei(200)
        assertEquals(1,db.dao().healthObservations().count { it.kind==Kind.SLEEP.name })
    }
    @Test fun withdrawalClearsRawDerivedAndSnapshotDataAndKeepsHardwareAndLegacyHealthConnect()=runBlocking {
        otherSources();huawei(500)
        val built=repo.currentDay(date)
        assertEquals(500L,built.metrics.first { it.key=="steps" }.value)
        assertNotNull(repo.cachedDay(date))
        sink.retain(emptySet())
        assertNull(repo.cachedDay(date))
        assertEquals(2,db.dao().observationCount(day.start,day.end))
        val restored=repo.currentDay(date)
        assertEquals(300L,restored.metrics.first { it.key=="steps" }.value)
        assertTrue(restored.episodes.none { HUAWEI_HEALTH in it.qualityFlags })
        assertEquals(1,db.openHelper.readableDatabase.version)
    }
    @Test fun emptyIncompleteSnapshotDoesNotDeleteEarlierSessions()=runBlocking {
        val sleep=HuaweiRecord("sleep",Kind.SLEEP,day.start+1000,day.start+2000,label="华为运动健康")
        sink.save("a",HuaweiScope.SLEEP,day,HuaweiPage(listOf(sleep)),zone,System.currentTimeMillis(),0)
        sink.save("a",HuaweiScope.SLEEP,day,HuaweiPage(emptyList()),zone,System.currentTimeMillis(),0)
        assertEquals(1,db.dao().healthObservations().size)
        sink.retain(setOf(Kind.HEALTH_STEPS));assertTrue(db.dao().healthObservations().isEmpty())
    }
    @Test fun crossMidnightSessionsUpsertByAccountKindAndNativeId()=runBlocking {
        val sleep=HuaweiRecord("sleep-1",Kind.SLEEP,day.start-3_600_000,day.start+6*3_600_000,label="华为运动健康")
        repeat(2) { sink.save("a",HuaweiScope.SLEEP,day,HuaweiPage(listOf(sleep)),zone,System.currentTimeMillis(),0) }
        assertEquals(1,db.dao().healthObservations().size)
        assertEquals(1,repo.currentDay(date).episodes.count { it.kind=="SLEEP" })
    }
    @Test fun pauseGenerationPreventsLateIngestionAndDeletedRecordsDoNotReimport()=runBlocking {
        val generation=repo.cachedPolicy.generation
        repo.command("pause")
        val record=HuaweiRecord("daily:$date",Kind.HEALTH_STEPS,day.start,day.end,100,"华为运动健康")
        sink.save("a",HuaweiScope.STEPS,day,HuaweiPage(listOf(record),true),zone,System.currentTimeMillis(),generation)
        assertTrue(db.dao().healthObservations().isEmpty())
        repo.command("resume");huawei(100)
        repo.delete(repo.preview(day.start,day.end));huawei(100)
        assertTrue(db.dao().healthObservations().isEmpty())
    }
}

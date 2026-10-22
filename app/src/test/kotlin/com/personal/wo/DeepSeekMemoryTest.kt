package com.personal.wo

import android.app.Application
import androidx.room.Room
import com.personal.wo.core.*
import com.personal.wo.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class DeepSeekMemoryTest {
    private val name="memory-${UUID.randomUUID()}.db"
    private lateinit var db:WoDatabase
    private lateinit var repo:WoRepository
    private val zone=ZoneId.systemDefault()
    private val date=LocalDate.now(zone).minusDays(1)
    private fun open() {
        val app=RuntimeEnvironment.getApplication()
        db=Room.databaseBuilder(app,WoDatabase::class.java,name).allowMainThreadQueries().build()
        repo=WoRepository(db,KeyVault(app),EngineConfig(),app)
    }
    @Before fun setup()=runBlocking {
        open();repo.initialize();repo.command("start")
        val span=window(date,zone)
        repo.ingest(listOf(Observation("test-steps",Source.STEPS,"test-steps",Kind.STEP_DELTA,span.start+1000,span.start+2000,payload=Payload(steps=100))),repo.cachedPolicy.generation)
        repo.currentDay(date)
    }
    @After fun cleanup() { db.close();RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun response()=buildJsonObject {
        put("choices",buildJsonArray { add(buildJsonObject {
            put("finish_reason","stop");put("message",buildJsonObject { put("content",buildJsonObject {
                put("summary","$date 的记录显示步行100步。")
                put("observations",buildJsonArray { add(buildJsonObject { put("text","$date 有步行记录。")
                    put("dates",buildJsonArray { add(date.toString()) }) }) })
            }.toString()) })
        }) })
    }.toString()
    private fun service(complete:suspend(DeepSeekConfig,JsonObject)->String={_,_->response()})=DeepSeekService(repo,
        readConfig={DeepSeekConfig("synthetic-test-key",enabled=true,autoReview=true)},complete=complete)
    @Test fun memorySurvivesDatabaseCloseAndReopenAndIsIncludedInTheNextCall()=runBlocking {
        service().memory.saveNotes("关注步行目标")
        service().review()
        db.close();open();repo.initialize()
        val restored=service().memory.read()
        assertEquals("关注步行目标",restored.notes);assertEquals(1,restored.observations.size)
        service { _,request ->
            assertTrue(request.toString().contains("关注步行目标"));assertTrue(request.toString().contains("有步行记录"));response()
        }.review()
    }
    @Test fun networkFailureKeepsExistingMemory()=runBlocking {
        service().review();val before=service().memory.read()
        try { service { _,_->throw java.io.IOException("network") }.review();fail("must fail") } catch(_:java.io.IOException) { }
        assertEquals(before,service().memory.read())
    }
    @Test fun deletionClearsInferencesAndRejectsAnInFlightResponseButKeepsUserNotes()=runBlocking {
        service().memory.saveNotes("关注步行目标");service().review()
        val span=window(date,zone)
        try { service { _,_->repo.delete(repo.preview(span.start,span.end));response() }.review();fail("must reject stale response") }
        catch(_:IllegalStateException) { }
        val memory=service().memory.read()
        assertEquals("关注步行目标",memory.notes);assertTrue(memory.observations.isEmpty());assertNull(memory.review)
    }
    @Test fun clearWhileARequestRunsCannotResurrectForgottenMemory()=runBlocking {
        service().memory.saveNotes("old note")
        try { service { _,_->service().memory.clearAll();response() }.review();fail("must reject") }
        catch(_:IllegalStateException) { }
        assertEquals("",service().memory.read().notes);assertNull(service().memory.read().review)
    }
    @Test fun automaticReviewAttemptsOnlyOncePerDayAndRespectsRecordingPause()=runBlocking {
        var calls=0
        val service=service { _,_->calls++;response() }
        service.review(automatic=true);service.review(automatic=true)
        assertEquals(1,calls)
        repo.command("pause");assertNull(service.review(automatic=true));assertEquals(1,calls)
        assertEquals(1,service.memory.read().observations.size)
    }
    @Test fun backupsContainMemoryButNeverTheProviderCredential()=runBlocking {
        service().memory.saveNotes("长期目标")
        val encoded=WoJson.encodeToString(BackupPolicy(repo.cachedPolicy,"seed",service().memory.read()))
        assertTrue(encoded.contains("长期目标"));assertFalse(encoded.contains("synthetic-test-key"))
        val legacy=WoJson.decodeFromString(BackupPolicy.serializer(),"""{"policy":{},"sourceKeySeed":"seed"}""")
        assertNull(legacy.memory)
    }
}

package com.personal.wo.core

import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.*

class SharedDataTest {
    private class Files:SharedFileStore {
        var private=true;var failIndex=false
        val files=mutableMapOf<String,String>();val writes=mutableListOf<String>()
        override suspend fun requirePrivate() { check(private) }
        override suspend fun read(path:String)=files[path]
        override suspend fun write(path:String,text:String) { if(path=="wo/index.json"&&failIndex)error("offline");writes+=path;files[path]=text }
        override suspend fun delete(path:String) { files.remove(path) }
    }
    private fun day(date:String="2026-10-22")=demoDay(LocalDate.parse(date),"Asia/Shanghai")
    // In-memory stores complete synchronously; no coroutine runtime is needed by the core module.
    private fun <T> run(block:suspend()->T):T {
        var outcome:Result<T>?=null
        block.startCoroutine(object:Continuation<T> {
            override val context=EmptyCoroutineContext
            override fun resumeWith(result:Result<T>) { outcome=result }
        })
        return checkNotNull(outcome).getOrThrow()
    }
    @Test fun publicDestinationIsRejectedBeforeAnyRecordIsWritten() {
        val files=Files().apply { private=false }
        assertThrows(IllegalStateException::class.java) { run { SharedDataPublisher(files).publish(listOf("2026-10-22"),"Asia/Shanghai",{day()}) } }
        assertTrue(files.writes.isEmpty())
    }
    @Test fun uploadCanResumeInANewInstanceAndUnchangedDaysDoNotCreateCommits() {
        val files=Files();run { SharedDataPublisher(files).publish(listOf("2026-10-22"),"Asia/Shanghai",{day()}) }
        assertEquals(listOf("wo/days/2026-10-22.json","wo/index.json"),files.writes)
        files.writes.clear()
        val result=run { SharedDataPublisher(files).publish(listOf("2026-10-22"),"Asia/Shanghai",{day().copy(asOfMs=day().asOfMs+1000)}) }
        assertEquals(0,result.uploaded);assertTrue(files.writes.isEmpty())
    }
    @Test fun failedIndexPublicationCanBeRetriedWithoutLosingEarlierDates() {
        val files=Files();run { SharedDataPublisher(files).publish(listOf("2026-10-21"),"Asia/Shanghai",{day(it)}) }
        files.failIndex=true
        assertThrows(IllegalStateException::class.java) { run { SharedDataPublisher(files).publish(listOf("2026-10-22","2026-10-21"),"Asia/Shanghai",{day(it)}) } }
        assertEquals(1,WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).days.size)
        files.failIndex=false
        run { SharedDataPublisher(files).publish(listOf("2026-10-22","2026-10-21"),"Asia/Shanghai",{day(it)}) }
        assertEquals(2,WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).days.size)
    }
    @Test fun withdrawnDatesDisappearFromTheLiveIndexAndDayFiles() {
        val files=Files();run { SharedDataPublisher(files).publish(listOf("2026-10-21","2026-10-22"),"Asia/Shanghai",{day(it)}) }
        run { SharedDataPublisher(files).publish(listOf("2026-10-22"),"Asia/Shanghai",{day(it)}) }
        assertFalse(files.files.containsKey("wo/days/2026-10-21.json"))
        assertEquals(listOf("2026-10-22"),WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).days.map { it.date })
    }
    @Test fun pendingDatesAreReportedAndIndexOnlyAdvertisesUploadedFiles() {
        val files=Files();val result=run { SharedDataPublisher(files).publish(listOf("2026-10-22","2026-10-21"),"Asia/Shanghai",{day(it)},1) }
        assertEquals(1,result.pending)
        val index=WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json"))
        assertTrue(index.days.all { files.files.containsKey(it.path) });assertEquals(1,index.days.size)
    }
    @Test fun rawNotificationTextCoordinatesAndMediaUrisAreNotShared() {
        val base=day();val source=base.episodes.first().copy(detail="PRIVATE_NOTICE_TEXT",lat=39.1234567,lon=116.7654321,photoUris=listOf("content://private-photo"))
        val text=sharedDay(base.copy(episodes=listOf(source,source.copy(id="notice",kind="NOTICE",title="PRIVATE_NOTICE_TITLE")))).toString()
        listOf("PRIVATE_NOTICE_TEXT","PRIVATE_NOTICE_TITLE","39.1234567","116.7654321","content://private-photo").forEach { assertFalse(text.contains(it)) }
        assertTrue(text.contains(source.title));assertTrue(text.contains(base.dayDate))
    }
    @Test fun sharedMemoryIsPublishedBeforeItsIndexAndClearingItReplacesTheLiveFile() {
        val files=Files()
        run { SharedDataPublisher(files).publish(listOf(day().dayDate),day().zoneId,{day()},memory=LifeMemory(notes="关注阅读")) }
        assertTrue(files.writes.indexOf("wo/memory.json")<files.writes.indexOf("wo/index.json"))
        val index=WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json"))
        assertEquals("wo/memory.json",index.memory!!.path)
        files.writes.clear()
        run { SharedDataPublisher(files).publish(listOf(day().dayDate),day().zoneId,{day()},memory=LifeMemory(notes="关注阅读")) }
        assertTrue(files.writes.isEmpty())
        run { SharedDataPublisher(files).publish(listOf(day().dayDate),day().zoneId,{day()},memory=LifeMemory(revision=1)) }
        assertEquals("",WoJson.decodeFromString<LifeMemory>(files.files.getValue("wo/memory.json")).notes)
        assertEquals(listOf("wo/memory.json","wo/index.json"),files.writes)
    }
}

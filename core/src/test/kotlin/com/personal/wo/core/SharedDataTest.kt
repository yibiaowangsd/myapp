package com.personal.wo.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.*

class SharedDataTest {
    private class Files:SharedFileStore {
        var private=true;var failIndex=false;var failMemory=false
        val files=mutableMapOf<String,String>();val writes=mutableListOf<String>()
        override suspend fun requirePrivate() { check(private) }
        override suspend fun read(path:String)=files[path]
        override suspend fun commit(changes:Map<String,String>,deletions:Set<String>) {
            if(("wo/index.json" in changes&&failIndex)||("wo/memory.json" in changes&&failMemory))error("offline")
            val next=files.toMutableMap();deletions.forEach { next.remove(it) };next.putAll(changes)
            files.clear();files.putAll(next);writes+=changes.keys
        }
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
    @Test fun failedMemoryLeavesExistingDayAndIndexUnchangedAndRetryPublishesBoth() {
        val files=Files();val publisher=SharedDataPublisher(files)
        run { publisher.publish(listOf(day().dayDate),day().zoneId,{day()},memory=LifeMemory()) }
        val before=files.files.toMap();files.failMemory=true
        assertThrows(IllegalStateException::class.java) { run {
            publisher.publish(listOf(day().dayDate),day().zoneId,{day().copy(headline="changed")},memory=LifeMemory(notes="new",revision=1))
        } }
        assertEquals(before,files.files);files.failMemory=false
        run { publisher.publish(listOf(day().dayDate),day().zoneId,{day().copy(headline="changed")},memory=LifeMemory(notes="new",revision=1)) }
        val index=WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json"))
        assertEquals(sharedHash(files.files.getValue("wo/memory.json")),index.memory!!.hash)
        assertEquals(sharedDayHash(WoJson.parseToJsonElement(files.files.getValue(index.days.single().path)).jsonObject),index.days.single().hash)
    }
    @Test fun failedIndexCannotWithdrawAFileOrOverwriteAnIndexedDay() {
        val files=Files();run { SharedDataPublisher(files).publish(listOf("2026-10-21","2026-10-22"),day().zoneId,{day(it)}) }
        val before=files.files.toMap();files.failIndex=true
        assertThrows(IllegalStateException::class.java) { run { SharedDataPublisher(files).publish(listOf("2026-10-22"),day().zoneId,{day(it).copy(headline="changed")}) } }
        assertEquals(before,files.files)
    }
    @Test fun pendingUpdatesKeepThePreviouslyPublishedSnapshot() {
        val dates=listOf("2026-10-22","2026-10-21");val files=Files()
        run { SharedDataPublisher(files).publish(dates,day().zoneId,{day(it)}) }
        val previous=WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).days.last()
        val result=run { SharedDataPublisher(files).publish(dates,day().zoneId,{day(it).copy(headline="changed")},maxChanges=1) }
        assertEquals(1,result.pending)
        assertEquals(previous,WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).days.last())
    }
    @Test fun badHistoricalDateDoesNotBlockOtherDatesOrMemoryAndCanRecover() {
        val files=Files();val dates=listOf("2026-10-22","2026-10-21")
        val result=run { SharedDataPublisher(files).publish(dates,day().zoneId,{if(it.endsWith("21"))error("unreadable") else day(it)},memory=LifeMemory(notes="saved")) }
        assertEquals(1,result.pending);assertEquals(listOf("2026-10-21"),result.failedDates)
        assertTrue(files.files.containsKey("wo/memory.json"))
        assertEquals(listOf("2026-10-21"),WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).pendingDates)
        val recovered=run { SharedDataPublisher(files).publish(dates,day().zoneId,{day(it)},memory=LifeMemory(notes="saved")) }
        assertEquals(0,recovered.pending);assertEquals(2,recovered.total)
        assertTrue(WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).pendingDates.isEmpty())
    }
    @Test fun largeUnicodeDayIsSplitIntoReadableJsonAndRoundTripsWithoutLoss() {
        val base=day();val episode=base.episodes.first()
        val large=base.copy(episodes=(0..4000).map { episode.copy(id="event-$it",title="活动🌏".repeat(20)) })
        val payload=sharedDay(large);assertTrue(payload.toString().toByteArray().size>SHARED_FILE_BYTES)
        val files=sharedDayFiles(payload)
        assertTrue(files.size>2);assertTrue(files.values.all { it.toByteArray().size<=SHARED_FILE_BYTES })
        files.values.forEach { WoJson.parseToJsonElement(it).jsonObject }
        assertEquals(payload,run { readSharedDay(files.getValue(sharedDayPath(base.dayDate))) { files[it] } })
        val part=files.keys.first { it.startsWith("wo/parts/") }
        assertThrows(IllegalArgumentException::class.java) { run { readSharedDay(files.getValue(sharedDayPath(base.dayDate))) { if(it==part)"{}" else files[it] } } }
    }
    @Test fun orphanedLegacyFilesAreAdoptedWithoutReuploadingAndVersionOneCanMigrate() {
        val files=Files();files.files[sharedDayPath(day().dayDate)]=sharedDay(day()).toString()
        run { SharedDataPublisher(files).publish(listOf(day().dayDate),day().zoneId,{day()},memory=LifeMemory()) }
        assertEquals(listOf("wo/memory.json","wo/index.json"),files.writes)
        val index=WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json"))
        files.files["wo/index.json"]=WoJson.encodeToString(SharedDataIndex.serializer(),index.copy(schemaVersion=1))
        files.writes.clear();run { SharedDataPublisher(files).publish(listOf(day().dayDate),day().zoneId,{day()},memory=LifeMemory()) }
        assertEquals(listOf("wo/index.json"),files.writes)
    }
    @Test fun moreThanFortyEightDaysContinueAcrossPublisherInstances() {
        val files=Files();val dates=(0..51).map { LocalDate.parse("2026-10-22").minusDays(it.toLong()).toString() }
        val first=run { SharedDataPublisher(files).publish(dates,day().zoneId,{day(it)}) }
        assertEquals(4,first.pending);assertEquals(48,first.total)
        val next=run { SharedDataPublisher(files).publish(dates,day().zoneId,{day(it)}) }
        assertEquals(0,next.pending);assertEquals(52,next.total);assertEquals(4,next.uploaded)
    }
    @Test fun cancellationIsNeverTurnedIntoASuccessfulPartialBatch() {
        val files=Files()
        assertThrows(java.util.concurrent.CancellationException::class.java) { run {
            SharedDataPublisher(files).publish(listOf(day().dayDate),day().zoneId,{throw java.util.concurrent.CancellationException()})
        } }
        assertTrue(files.files.isEmpty())
    }
    @Test fun shrinkingOrWithdrawingAShardedDayRemovesItsOldPartsAtomically() {
        val files=Files();val base=day();val large=base.copy(episodes=(0..2500).map { base.episodes.first().copy(id="event-$it",title="事件".repeat(50)) })
        run { SharedDataPublisher(files).publish(listOf(base.dayDate),base.zoneId,{large}) }
        assertTrue(files.files.keys.any { it.startsWith("wo/parts/") })
        val before=files.files.toMap();files.failIndex=true
        assertThrows(IllegalStateException::class.java) { run { SharedDataPublisher(files).publish(emptyList(),base.zoneId,{large}) } }
        assertEquals(before,files.files);files.failIndex=false
        run { SharedDataPublisher(files).publish(listOf(base.dayDate),base.zoneId,{base}) }
        assertFalse(files.files.keys.any { it.startsWith("wo/parts/") })
        assertEquals(sharedDay(base),WoJson.parseToJsonElement(files.files.getValue(sharedDayPath(base.dayDate))))
    }
    @Test fun migratingVersionOneRepairsAMissingOrMismatchedFileEvenWhenItsIndexHashMatchesLocalData() {
        for(missing in listOf(false,true)) {
            val files=Files();val path=sharedDayPath(day().dayDate)
            val index=SharedDataIndex(archiveZone=day().zoneId,updatedAtMs=1,
                days=listOf(SharedDayEntry(day().dayDate,path,sharedDayHash(sharedDay(day())))))
            files.files["wo/index.json"]=WoJson.encodeToString(SharedDataIndex.serializer(),index)
            if(!missing)files.files[path]=sharedDay(day().copy(headline="partial old upload")).toString()
            run { SharedDataPublisher(files).publish(listOf(day().dayDate),day().zoneId,{day()}) }
            assertEquals(sharedDay(day()),WoJson.parseToJsonElement(files.files.getValue(path)))
            assertEquals(2,WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json")).schemaVersion)
        }
    }
    @Test fun deferredCorruptLegacyDatesAreNotAdvertisedByTheMigratedIndex() {
        val files=Files();val dates=listOf("2026-10-22","2026-10-21")
        val entries=dates.map { SharedDayEntry(it,sharedDayPath(it),sharedDayHash(sharedDay(day(it)))) }
        files.files["wo/index.json"]=WoJson.encodeToString(SharedDataIndex.serializer(),SharedDataIndex(archiveZone=day().zoneId,updatedAtMs=1,days=entries))
        val first=run { SharedDataPublisher(files).publish(dates,day().zoneId,{day(it)},maxChanges=1) }
        assertEquals(1,first.pending)
        val index=WoJson.decodeFromString<SharedDataIndex>(files.files.getValue("wo/index.json"))
        assertEquals(listOf("2026-10-22"),index.days.map { it.date });assertEquals(listOf("2026-10-21"),index.pendingDates)
        val second=run { SharedDataPublisher(files).publish(dates,day().zoneId,{day(it)},maxChanges=1) }
        assertEquals(0,second.pending);assertEquals(2,second.total)
    }
}

package com.personal.wo.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LifeMemoryTest {
    private val day=demoDay(LocalDate.parse("2026-10-22"),"Asia/Shanghai")
    private fun response(date:String=day.dayDate,finish:String="stop")=buildJsonObject {
        put("choices",buildJsonArray { add(buildJsonObject {
            put("finish_reason",finish)
            put("message",buildJsonObject { put("content",buildJsonObject {
                put("summary","2026-10-22 有步行记录，不能据此判断长期习惯。")
                put("observations",buildJsonArray { add(buildJsonObject { put("text","10月22日的记录显示有步行活动。")
                    put("dates",buildJsonArray { add(date) }) }) })
            }.toString()) })
        }) })
    }.toString()
    @Test fun persistedMemoryIsReplayedInANewRequestWithoutAnyChatSession() {
        val initial=LifeMemory(notes="希望每周读书三次")
        val saved=acceptDeepSeekMemory(lifeMemoryContext(initial,listOf(day)),response(),"deepseek-flash",10)
        val restored=WoJson.decodeFromString<LifeMemory>(WoJson.encodeToString(saved))
        val request=deepSeekMemoryRequest(lifeMemoryContext(restored,listOf(day)),"deepseek-flash")
        val input=Json.parseToJsonElement(request.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content).jsonObject
        assertEquals(initial.notes,input.getValue("userNotes").jsonPrimitive.content)
        assertEquals(1,input.getValue("previousObservations").jsonArray.size)
        assertEquals(saved.review!!.text,input.getValue("previousReview").jsonPrimitive.content)
        assertFalse(request.toString().contains("reasoning_content"))
    }
    @Test fun truncationAndInventedSourceDatesCannotBecomeMemory() {
        val context=lifeMemoryContext(LifeMemory(),listOf(day))
        assertThrows(IllegalArgumentException::class.java) { acceptDeepSeekMemory(context,response(finish="length"),"deepseek-flash",10) }
        assertThrows(IllegalArgumentException::class.java) { acceptDeepSeekMemory(context,response(date="2020-01-01"),"deepseek-flash",10) }
        assertEquals(LifeMemory(),context.memory)
    }
    @Test fun duplicateObservationsDoNotGrowMemoryAndOldEntriesAreBounded() {
        val context=lifeMemoryContext(LifeMemory(observations=(1..200).map { LifeObservation("观察$it",listOf(day.dayDate),it.toLong()) }),listOf(day))
        val next=acceptDeepSeekMemory(context,response(),"deepseek-flash",300)
        val again=acceptDeepSeekMemory(lifeMemoryContext(next,listOf(day)),response(),"deepseek-flash",400)
        assertEquals(200,again.observations.size)
        assertEquals(1,again.observations.count { it.text=="10月22日的记录显示有步行活动。" })
    }
    @Test fun contextUsesStructuredRecordsAndOmitsPrivateRawFields() {
        val episode=day.episodes.first().copy(detail="SECRET_BODY",lat=39.123456,lon=116.123456,photoUris=listOf("content://secret"))
        val privateDay=day.copy(episodes=listOf(episode,episode.copy(kind="NOTICE",title="SECRET_NOTICE")))
        val request=deepSeekMemoryRequest(lifeMemoryContext(LifeMemory(),listOf(privateDay)),"deepseek-flash").toString()
        listOf("SECRET_BODY","SECRET_NOTICE","content://secret","39.123456","116.123456").forEach { assertFalse(request.contains(it)) }
    }
    @Test fun oversizedHistoryIsBoundedWithoutCuttingTheUsersSavedNotes() {
        val notes="记住我的目标".repeat(500)
        val context=lifeMemoryContext(LifeMemory(notes=notes),List(90) { day.copy(dayDate=LocalDate.parse(day.dayDate).minusDays(it.toLong()).toString()) })
        assertTrue(context.days.size<=30)
        assertTrue(deepSeekMemoryRequest(context,"deepseek-flash").toString().contains(notes))
    }
}

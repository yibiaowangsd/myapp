package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

class DayPerformanceTest {
    @Test fun fasterHexPreservesEveryByteAndExistingStableIds() {
        val bytes=ByteArray(256){it.toByte()}
        assertEquals(bytes.joinToString(""){"%02x".format(it)},bytes.lowerHex())
        assertEquals("",byteArrayOf().lowerHex())
        val values=arrayOf<Any?>("照片",null,123456789L,"application.activity",true)
        val old=MessageDigest.getInstance("SHA-256").digest(values.joinToString("\u0000").toByteArray()).take(16).joinToString(""){"%02x".format(it)}
        assertEquals(old,stableId(*values))
    }

    @Test fun reusingComputedEpisodesPreservesDayMetricsCorrectionsAndBoundaries() {
        val date=LocalDate.of(2026,10,16);val policy=Policy(archiveZone="Asia/Shanghai")
        val start=window(date,ZoneId.of(policy.archiveZone)).start
        val input=listOf(
            Observation("a",Source.USAGE,"a",Kind.RESUMED,start+3_600_000,payload=Payload(packageName="reader",className="main",label="阅读")),
            Observation("b",Source.USAGE,"b",Kind.PAUSED,start+7_200_000,payload=Payload(packageName="reader",className="main")),
            Observation("c",Source.PHOTO,"c",Kind.PHOTO,start+4_000_000,payload=Payload(uri="content://test/1",captured=true))
        )
        val engine=DayEngine();val now=start+9_000_000
        val fresh=engine.episodes(input,policy,emptyList(),now)
        val annotation=listOf(Annotation("edit",fresh.first().id,1,title="修正阅读",confirmed=true))
        val normal=engine.build(date,policy,input,emptyList(),emptyList(),emptyList(),annotation,now)
        val reused=engine.build(date,policy,input,emptyList(),emptyList(),emptyList(),annotation,now,freshEpisodes=fresh)
        assertEquals(normal,reused)
        assertEquals(3_600_000L,reused.metrics.first{it.key=="appMs"}.value)
    }
}

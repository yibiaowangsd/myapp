package com.personal.wo.data

import com.personal.wo.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import javax.net.ssl.HttpsURLConnection

@Serializable data class DeepSeekConfig(
    val apiKey:String="",val model:String="deepseek-flash",val enabled:Boolean=false,val autoReview:Boolean=false
) {
    fun checked():DeepSeekConfig {
        require(model in listOf("deepseek-flash","deepseek-v4-pro")) { "请选择受支持的 DeepSeek 模型" }
        require(apiKey.length in 8..2048&&apiKey.all { it.code in 33..126 }) { "请填写有效的 DeepSeek API Key" }
        return this
    }
}

internal suspend fun requestDeepSeek(config:DeepSeekConfig,body:JsonObject):String {
    config.checked();currentCoroutineContext().ensureActive()
    val connection=URL("https://api.deepseek.com/chat/completions").openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects=false;connection.requestMethod="POST"
        connection.connectTimeout=15000;connection.readTimeout=60000
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
        connection.setRequestProperty("Authorization","Bearer ${config.apiKey}")
        connection.doOutput=true
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status=connection.responseCode
        check(status in 200..299) { when(status) {
            401,403->"DeepSeek 拒绝访问，请检查 API Key"
            402->"DeepSeek 账户余额不足"
            429->"DeepSeek 请求过于频繁，请稍后重试"
            else->"DeepSeek 暂时未完成请求（HTTP $status）"
        } }
        val bytes=connection.inputStream.use { input ->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {
                currentCoroutineContext().ensureActive()
                val count=input.read(buffer);if(count<0)break
                require(out.size()+count<=256*1024) { "DeepSeek 响应过大，本次结果未保存" }
                out.write(buffer,0,count)
            }
            out.toByteArray()
        }
        currentCoroutineContext().ensureActive()
        return bytes.toString(Charsets.UTF_8)
    } finally { connection.disconnect() }
}

class DeepSeekService(
    private val repo:WoRepository,val memory:LifeMemoryStore=LifeMemoryStore(repo),
    private val readConfig:()->DeepSeekConfig={repo.vault.readProtected("deepseek-config")?.let { WoJson.decodeFromString<DeepSeekConfig>(it.decodeToString()) } ?: DeepSeekConfig()},
    private val complete:suspend(DeepSeekConfig,JsonObject)->String=::requestDeepSeek
) {
    private val mutex=Mutex()
    fun config():DeepSeekConfig=readConfig()
    fun configure(apiKey:String,model:String,enabled:Boolean,autoReview:Boolean) {
        val next=DeepSeekConfig(apiKey.trim().ifBlank { config().apiKey },model,enabled,autoReview)
        if(enabled)next.checked()
        repo.vault.writeProtected("deepseek-config",WoJson.encodeToString(next).toByteArray())
    }
    suspend fun review(automatic:Boolean=false):LifeMemory?=mutex.withLock {
        val selected=config();check(selected.enabled) { "请先保存并开启 DeepSeek" };selected.checked()
        if(automatic&&(!selected.autoReview||repo.cachedPolicy.paused||!repo.cachedPolicy.requested))return@withLock null
        val generation=repo.cachedPolicy.generation
        val zone=ZoneId.of(repo.cachedPolicy.archiveZone);val today=LocalDate.now(zone)
        val through=if(automatic)today.minusDays(1) else today
        if(!automatic)repo.currentDay(today)
        val dates=(repo.db.dao().summaryDates(zone.id)+repo.dirtyDays().map { it.toString() }).distinct()
            .filter { it<=through.toString() }.sortedDescending().take(30)
        val days=dates.map { repo.currentDay(LocalDate.parse(it)) }
            .filter { day->day.facts.isNotEmpty()||day.metrics.any { it.value!=null }||day.episodes.any { it.kind!="NOTICE" } }
        if(automatic&&days.none { it.dayDate==through.toString() })return@withLock null
        val before=memory.read();val context=lifeMemoryContext(before,days)
        check(repo.cachedPolicy.generation==generation&&config()==selected) { "配置已变化，请重新生成" }
        // Mark before sending: background restarts cannot repeatedly bill the same day after a timeout.
        if(automatic) {
            val shouldSend=repo.mutex.withLock {
                val key="deepseek.auto-attempt";val value="$zone:$through"
                if(repo.db.dao().meta(key)?.value==value)false else { repo.db.dao().putMeta(MetaRow(key,value));true }
            }
            if(!shouldSend)return@withLock null
        }
        val response=complete(selected,deepSeekMemoryRequest(context,selected.model))
        currentCoroutineContext().ensureActive()
        check(config()==selected) { "DeepSeek 已关闭或配置变化，本次结果未保存" }
        val next=acceptDeepSeekMemory(context,response,selected.model,System.currentTimeMillis())
        memory.commit(next,before.revision,generation)
    }
}

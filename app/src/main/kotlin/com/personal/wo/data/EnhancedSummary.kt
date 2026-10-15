package com.personal.wo.data

import com.personal.wo.BuildConfig
import com.personal.wo.core.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.withLock
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Serializable data class EnhancedConfig(val endpoint:String="",val apiKey:String="",val model:String="")
@Serializable data class FactPlan(val factIds:List<String>)
class EnhancedSummary(private val repo:WoRepository) {
    fun config():EnhancedConfig = repo.vault.readProtected("enhanced-config")?.let { WoJson.decodeFromString<EnhancedConfig>(it.decodeToString()) } ?: EnhancedConfig()
    fun save(c:EnhancedConfig) {
        if(c.endpoint.isNotBlank())require(URL(c.endpoint).protocol=="https") { "增强端点必须使用HTTPS" }
        repo.vault.writeProtected("enhanced-config",WoJson.encodeToString(c).toByteArray())
    }
    suspend fun arrange(day:DayView):List<Sentence> {
        check(BuildConfig.ENHANCED) { "离线版未开放网络增强" }
        val config=config();check(config.endpoint.isNotBlank()) { "先配置事实整理端点" }
        val generation=repo.cachedPolicy.generation
        repo.mutex.withLock {
            val today=java.time.LocalDate.now(java.time.ZoneId.of(repo.cachedPolicy.archiveZone))
            val key="enhanced.count.$today";val count=repo.db.dao().meta(key)?.value?.toIntOrNull() ?: 0
            check(count<3) { "今天已达到3次增强请求上限，仍可使用本地摘要" };repo.db.dao().putMeta(MetaRow(key,(count+1).toString()))
        }
        val request=buildJsonObject {
            put("task","arrangeFacts");put("model",config.model)
            put("facts",JsonArray(day.facts.map { f->buildJsonObject { put("id",f.id);put("type",f.type);put("value",f.displayValue) } }))
            put("outputContract","Return only a JSON object with factIds, a unique ordered list drawn from the provided IDs. Do not generate text or new facts.")
        }.toString().toByteArray();require(request.size<=12288)
        val connection=URL(config.endpoint).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod="POST";connection.connectTimeout=10000;connection.readTimeout=20000;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json");if(config.apiKey.isNotBlank())connection.setRequestProperty("Authorization","Bearer ${config.apiKey}")
            connection.outputStream.use { it.write(request) };check(connection.responseCode in 200..299) { "增强端点暂时不可用，已保留本地摘要" }
            val bytes=connection.inputStream.use { input->val b=ByteArray(16385);var n=0;while(n<b.size){val r=input.read(b,n,b.size-n);if(r<0)break;n+=r};require(n<=16384);b.copyOf(n) }
            val plan=WoJson.decodeFromString<FactPlan>(bytes.decodeToString());val ids=day.facts.map { it.id }.toSet()
            require(plan.factIds.size<=4&&plan.factIds.toSet().size==plan.factIds.size&&plan.factIds.all { it in ids }) { "增强结果含未知或重复事实，已保留本地摘要" }
            require(repo.cachedPolicy.generation==generation) { "记录已变化，本次结果不应用" }
            // Only reorder locally rendered sentences. The model cannot supply wording, numbers, or database facts.
            return day.sentences.sortedBy { sentence->plan.factIds.indexOfFirst { it in sentence.factIds }.let { if(it<0)Int.MAX_VALUE else it } }
        } finally { connection.disconnect() }
    }
}

package com.personal.wo.data

import com.personal.wo.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.ZoneId

@Serializable data class SharedSyncConfig(val repository:String="",val token:String="",val enabled:Boolean=false) {
    fun checked():SharedSyncConfig {
        require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}"))) { "请填写 owner/repository 格式的私有仓库" }
        require(!repository.endsWith("/.git")&&!repository.endsWith("/..")) { "仓库名称不正确" }
        require(token.isNotBlank()) { "请填写仅授权此私有仓库的 GitHub 访问令牌" }
        return this
    }
}
data class SharedSyncStatus(val lastSuccessMs:Long=0,val days:Int=0,val pending:Int=0,val message:String="尚未同步")


class SharedDataSync(private val repo:WoRepository) {
    private val mutex=Mutex()
    fun config():SharedSyncConfig=repo.vault.readProtected("shared-sync")?.let { WoJson.decodeFromString<SharedSyncConfig>(it.decodeToString()) } ?: SharedSyncConfig()
    suspend fun configure(repository:String,token:String,enabled:Boolean) {
        val old=config();val same=repository.trim().equals(old.repository,true)
        val next=SharedSyncConfig(repository.trim(),token.trim().ifBlank { if(same)old.token else "" },enabled)
        if(enabled)next.checked()
        repo.vault.writeProtected("shared-sync",WoJson.encodeToString(SharedSyncConfig.serializer(),next).toByteArray())
        if(!same)repo.db.dao().deleteMetaRange("shared-sync.status","shared-sync.status")
    }
    suspend fun status():SharedSyncStatus {
        val row=repo.db.dao().meta("shared-sync.status")?.value ?: return SharedSyncStatus()
        val fields=WoJson.parseToJsonElement(row).jsonObject
        return SharedSyncStatus(fields["lastSuccessMs"]!!.jsonPrimitive.long,fields["days"]!!.jsonPrimitive.int,
            fields["pending"]!!.jsonPrimitive.int,fields["message"]!!.jsonPrimitive.content)
    }
    private suspend fun saveStatus(status:SharedSyncStatus) {
        repo.db.dao().putMeta(MetaRow("shared-sync.status",buildJsonObject {
            put("lastSuccessMs",status.lastSuccessMs);put("days",status.days);put("pending",status.pending);put("message",status.message)
        }.toString()))
    }
    suspend fun sync():SharedSyncStatus=mutex.withLock {
        val selectedConfig=config();if(!selectedConfig.enabled)return@withLock status()
        val previous=status()
        try {
        saveStatus(previous.copy(message="同步进行中，当前批次尚未确认完整发布"))
        val generation=repo.cachedPolicy.generation
        val memory=LifeMemoryStore(repo).read()
        val zone=ZoneId.of(repo.cachedPolicy.archiveZone);val today=LocalDate.now(zone)
        repo.currentDay(today)
        val dates=(repo.db.dao().summaryDates(zone.id)+repo.dirtyDays().map { it.toString() }).distinct().sortedDescending()
        val files=GitHubSharedFiles(selectedConfig) { config()==selectedConfig&&repo.cachedPolicy.generation==generation&&LifeMemoryStore(repo).read().revision==memory.revision }
        val result=SharedDataPublisher(files).publish(dates,zone.id,{date->repo.currentDay(LocalDate.parse(date))},memory=memory)
        val now=System.currentTimeMillis()
        val status=SharedSyncStatus(if(result.pending==0)now else previous.lastSuccessMs,result.total,result.pending,
            if(result.pending==0)"已同步到私有仓库（含记忆与索引）" else "已发布 ${result.total} 天及记忆，另有 ${result.pending} 天待同步"+
                if(result.failedDates.isEmpty())"" else "（${result.failedDates.size} 天暂时无法生成，后续重试）")
        saveStatus(status)
        status
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) {
            val safe=if(e is SharedSyncException)e else SharedSyncException("准备同步",false,"数据校验或本地读取失败，请重试")
            // Do not overwrite the status of a newly selected repository.
            if(config()==selectedConfig)saveStatus(previous.copy(message="同步未完成；${safe.message}"))
            throw safe
        }
    }
}

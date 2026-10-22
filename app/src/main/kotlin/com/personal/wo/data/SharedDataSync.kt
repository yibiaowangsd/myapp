package com.personal.wo.data

import com.personal.wo.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

@Serializable data class SharedSyncConfig(val repository:String="",val token:String="",val enabled:Boolean=false) {
    fun checked():SharedSyncConfig {
        require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}"))) { "请填写 owner/repository 格式的私有仓库" }
        require(!repository.endsWith("/.git")&&!repository.endsWith("/..")) { "仓库名称不正确" }
        require(token.isNotBlank()) { "请填写仅授权此私有仓库的 GitHub 访问令牌" }
        return this
    }
}
data class SharedSyncStatus(val lastSuccessMs:Long=0,val days:Int=0,val pending:Int=0,val message:String="尚未同步")

class GitHubSharedFiles(private val config:SharedSyncConfig,private val active:()->Boolean={true}):SharedFileStore {
    private val base="https://api.github.com/repos/${config.checked().repository}"
    private suspend fun request(method:String,path:String,body:JsonObject?=null,missing:Boolean=false):JsonObject? {
        currentCoroutineContext().ensureActive();check(active()) { "同步配置已变化，本次同步停止" }
        val connection=URL(base+path).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.requestMethod=method
            connection.connectTimeout=15000;connection.readTimeout=25000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version","2026-03-10")
            connection.setRequestProperty("Authorization","Bearer ${config.token}")
            if(body!=null) {
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status=connection.responseCode
            if(missing&&status==404)return null
            check(status in 200..299) { when(status) {
                401,403->"GitHub 拒绝访问，请检查令牌权限、有效期或请求额度"
                404->"仓库不存在，或令牌没有访问权限"
                409,422->"远端文件已变化，请稍后重新同步"
                else->"GitHub 同步未完成（HTTP $status）"
            } }
            val bytes=connection.inputStream.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2*1024*1024) { "远端响应过大" };out.write(buffer,0,n) }
                out.toByteArray()
            }
            return if(bytes.isEmpty())buildJsonObject{} else WoJson.parseToJsonElement(bytes.decodeToString()).jsonObject
        } finally { connection.disconnect() }
    }
    override suspend fun requirePrivate() {
        val metadata=request("GET","")!!
        require(metadata["private"]?.jsonPrimitive?.booleanOrNull==true) { "只允许同步到私有仓库；当前仓库不是私有仓库，未上传个人记录" }
    }
    private fun route(path:String):String {
        require(path=="wo/index.json"||path.matches(Regex("wo/days/[0-9]{4}-[0-9]{2}-[0-9]{2}\\.json")))
        return "/contents/$path"
    }
    private suspend fun file(path:String):JsonObject?=request("GET",route(path),missing=true)
    override suspend fun read(path:String):String?=file(path)?.let {
        require(it["type"]?.jsonPrimitive?.content=="file"&&it["encoding"]?.jsonPrimitive?.content=="base64") { "远端内容不是可读取的数据文件" }
        require(it["size"]!!.jsonPrimitive.int<=524288) { "远端文件过大" }
        Base64.getMimeDecoder().decode(it["content"]!!.jsonPrimitive.content).decodeToString()
    }
    override suspend fun write(path:String,text:String) {
        val bytes=text.toByteArray();require(bytes.size<=524288) { "当天结构化数据超过512KB，请缩小同步范围" }
        val previous=file(path);requirePrivate()
        request("PUT",route(path),buildJsonObject {
            put("message","Update life records");put("content",Base64.getEncoder().encodeToString(bytes))
            previous?.get("sha")?.let { put("sha",it) }
        })
    }
    override suspend fun delete(path:String) {
        val previous=file(path) ?: return
        requirePrivate()
        request("DELETE",route(path),buildJsonObject { put("message","Withdraw life records");put("sha",previous.getValue("sha")) })
    }
}

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
    suspend fun sync():SharedSyncStatus=mutex.withLock {
        val selectedConfig=config();if(!selectedConfig.enabled)return@withLock status()
        val generation=repo.cachedPolicy.generation
        val zone=ZoneId.of(repo.cachedPolicy.archiveZone);val today=LocalDate.now(zone)
        repo.currentDay(today)
        val dates=(repo.db.dao().summaryDates(zone.id)+repo.dirtyDays().map { it.toString() }).distinct().sortedDescending()
        val files=GitHubSharedFiles(selectedConfig) { config()==selectedConfig&&repo.cachedPolicy.generation==generation }
        val result=SharedDataPublisher(files).publish(dates,zone.id,{date->repo.currentDay(LocalDate.parse(date))})
        val now=System.currentTimeMillis()
        val status=SharedSyncStatus(now,result.total,result.pending,if(result.pending==0)"已同步，可在 ChatGPT 中读取" else "已同步 ${result.total} 天，另有 ${result.pending} 天待同步")
        repo.db.dao().putMeta(MetaRow("shared-sync.status",buildJsonObject {
            put("lastSuccessMs",now);put("days",status.days);put("pending",status.pending);put("message",status.message)
        }.toString()))
        status
    }
}

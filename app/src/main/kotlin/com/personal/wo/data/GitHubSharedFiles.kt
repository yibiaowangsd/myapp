package com.personal.wo.data

import com.personal.wo.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/** Messages are fixed strings; response bodies, file contents and credentials never enter errors. */
class SharedSyncException(val stage:String,val retryable:Boolean,message:String):IOException("$stage：$message")
internal data class SharedHttpResponse(val status:Int,val body:JsonObject,val retryAfterMs:Long=0,val limited:Boolean=false)
internal fun interface SharedGitHubTransport {
    suspend fun send(method:String,path:String,body:JsonObject?):SharedHttpResponse
}
private class GitHubTransport(private val config:SharedSyncConfig):SharedGitHubTransport {
    override suspend fun send(method:String,path:String,body:JsonObject?):SharedHttpResponse {
        val connection=URL("https://api.github.com/repos/${config.checked().repository}$path").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.requestMethod=method
            connection.connectTimeout=15000;connection.readTimeout=25000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28")
            connection.setRequestProperty("Authorization","Bearer ${config.token}")
            if(body!=null) {
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status=connection.responseCode
            // Do not parse GitHub error bodies: they may echo user data.
            if(status !in 200..299)return SharedHttpResponse(status,buildJsonObject {},
                retryAfterMs=(connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 0)*1000,
                limited=connection.getHeaderField("X-RateLimit-Remaining")=="0"||connection.getHeaderField("Retry-After")!=null)
            val bytes=connection.inputStream.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=input.read(buffer);if(n<0)break
                    if(out.size()+n>2*1024*1024)throw SharedSyncException("读取响应",false,"远端响应过大")
                    out.write(buffer,0,n)
                }
                out.toByteArray()
            }
            return SharedHttpResponse(status,if(bytes.isEmpty())buildJsonObject {} else WoJson.parseToJsonElement(bytes.decodeToString()).jsonObject)
        } finally { connection.disconnect() }
    }
}

class GitHubSharedFiles internal constructor(
    private val transport:SharedGitHubTransport,
    private val active:suspend()->Boolean={true},
    private val wait:suspend(Long)->Unit={delay(it)}
):SharedFileStore {
    constructor(config:SharedSyncConfig,active:suspend()->Boolean={true}):this(GitHubTransport(config),active)
    private var initialized=false
    private var branch=""
    private var head:String?=null
    private var tree:String?=null
    private suspend fun checkActive() {
        currentCoroutineContext().ensureActive()
        if(!active())throw SharedSyncException("快照检查",true,"记录、记忆或同步配置已变化，本批尚未发布，请重试")
    }
    private suspend fun request(stage:String,method:String,path:String,body:JsonObject?=null,missing:Boolean=false):JsonObject? {
        repeat(4) { attempt ->
            checkActive()
            val response=try { transport.send(method,path,body) }
            catch(e:SharedSyncException) { throw e }
            catch(e:IOException) {
                if(attempt==3)throw SharedSyncException(stage,true,"网络请求未完成，请重试")
                wait(1000L shl attempt);return@repeat
            }
            if(missing&&response.status==404)return null
            if(response.status in 200..299)return response.body
            val retryable=response.status in setOf(408,429,500,502,503,504)||(response.status==403&&response.limited)
            if(retryable&&attempt<3) { wait(maxOf(1000L shl attempt,response.retryAfterMs).coerceAtMost(30000));return@repeat }
            throw SharedSyncException(stage,retryable||response.status in setOf(409,422),when(response.status) {
                401,403->if(response.limited)"GitHub 请求额度不足，稍后重试" else "GitHub 拒绝访问，请检查私有仓库令牌权限或有效期"
                404->"仓库或文件不存在，或令牌没有访问权限"
                409,422->"远端分支已变化或提交被拒绝，本批未确认发布，请重新同步"
                else->"GitHub 请求未完成（HTTP ${response.status}）"
            })
        }
        error("unreachable")
    }
    private suspend fun metadata():JsonObject {
        val metadata=request("检查私有仓库","GET","")!!
        if(metadata["private"]?.jsonPrimitive?.booleanOrNull!=true)
            throw SharedSyncException("检查私有仓库",false,"只允许同步到私有仓库，未发布个人记录")
        return metadata
    }
    override suspend fun requirePrivate() {
        val metadata=metadata()
        if(initialized)return
        branch=metadata.getValue("default_branch").jsonPrimitive.content
        require(branch.matches(Regex("[A-Za-z0-9_./-]+"))) { "分支名称不兼容" }
        val ref=request("读取分支","GET","/git/ref/heads/$branch",missing=true)
        head=ref?.getValue("object")?.jsonObject?.getValue("sha")?.jsonPrimitive?.content
        head?.let { tree=request("读取提交","GET","/git/commits/$it")!!.getValue("tree").jsonObject.getValue("sha").jsonPrimitive.content }
        initialized=true
    }
    private fun checkedPath(path:String):String {
        require(path in setOf("wo/index.json","wo/memory.json")||
            path.matches(Regex("wo/days/[0-9]{4}-[0-9]{2}-[0-9]{2}\\.json"))||
            path.matches(Regex("wo/parts/[0-9]{4}-[0-9]{2}-[0-9]{2}/[a-f0-9]{64}/[0-9]{5}\\.json"))) { "数据路径不正确" }
        return path
    }
    override suspend fun read(path:String):String? {
        checkedPath(path);if(!initialized)requirePrivate()
        val snapshot=head ?: return null
        val file=request("读取数据文件","GET","/contents/$path?ref=$snapshot",missing=true) ?: return null
        require(file["type"]?.jsonPrimitive?.content=="file"&&file["encoding"]?.jsonPrimitive?.content=="base64") { "远端内容不是可读取的数据文件" }
        require(file.getValue("size").jsonPrimitive.int<=SHARED_FILE_BYTES) { "远端文件过大" }
        return Base64.getMimeDecoder().decode(file.getValue("content").jsonPrimitive.content).decodeToString()
    }
    override suspend fun commit(files:Map<String,String>,deletions:Set<String>) {
        (files.keys+deletions).forEach(::checkedPath)
        require(files.keys.intersect(deletions).isEmpty())
        files.values.forEach { require(it.toByteArray().size<=SHARED_FILE_BYTES) { "数据文件超过分片限制" } }
        if(files.isEmpty()&&deletions.isEmpty())return
        if(!initialized)requirePrivate()
        if(head==null) {
            // Git Data cannot create objects in an empty repository. Bootstrap a valid empty index.
            val intended=WoJson.decodeFromString<SharedDataIndex>(files.getValue("wo/index.json"))
            val empty=WoJson.encodeToString(SharedDataIndex.serializer(),intended.copy(days=emptyList(),memory=null))
            metadata()
            request("初始化目录","PUT","/contents/wo/index.json",buildJsonObject {
                put("message","Initialize private life data index");put("content",Base64.getEncoder().encodeToString(empty.toByteArray()));put("branch",branch)
            })
            initialized=false;requirePrivate()
        }
        val expected=head!!;val baseTree=tree!!
        val entries=buildJsonArray {
            for((path,text) in files) {
                metadata()
                val blob=request("上传数据对象","POST","/git/blobs",buildJsonObject { put("content",text);put("encoding","utf-8") })!!
                add(buildJsonObject { put("path",path);put("mode","100644");put("type","blob");put("sha",blob.getValue("sha")) })
            }
            deletions.forEach { path -> add(buildJsonObject { put("path",path);put("mode","100644");put("type","blob");put("sha",JsonNull) }) }
        }
        metadata()
        val newTree=request("创建数据树","POST","/git/trees",buildJsonObject { put("base_tree",baseTree);put("tree",entries) })!!.getValue("sha")
        val commit=request("创建完整快照","POST","/git/commits",buildJsonObject {
            put("message","Publish private life data snapshot");put("tree",newTree);put("parents",buildJsonArray { add(expected) })
        })!!.getValue("sha").jsonPrimitive.content
        metadata();checkActive()
        // Check the lease; force=false also rejects a race after this check.
        val live=request("检查分支版本","GET","/git/ref/heads/$branch")!!.getValue("object").jsonObject.getValue("sha").jsonPrimitive.content
        if(live!=expected)throw SharedSyncException("发布索引",true,"远端分支已变化，请重新同步以合并最新目录")
        try {
            request("发布索引与记忆","PATCH","/git/refs/heads/$branch",buildJsonObject { put("sha",commit);put("force",false) })
        } catch(e:SharedSyncException) {
            // An accepted PATCH can lose its response. Verify before reporting failure.
            val actual=request("确认发布结果","GET","/git/ref/heads/$branch")!!.getValue("object").jsonObject.getValue("sha").jsonPrimitive.content
            if(actual!=commit)throw e
        }
        head=commit;tree=newTree.jsonPrimitive.content
    }
}

package com.personal.wo

import com.personal.wo.core.*
import com.personal.wo.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Base64

class SharedGitHubFilesTest {
    private class Git:SharedGitHubTransport {
        var private=true;var live="initial";var failBlob=0;var dropPatchResponse=false;var race=false
        var requests=0;var patches=0;var mutateAtBlob:(()->Unit)?=null
        val calls=mutableListOf<String>()
        val snapshots=mutableMapOf("initial" to mapOf("wo/memory.json" to "old-memory"))
        val blobs=mutableMapOf<String,String>();val trees=mutableMapOf<String,Map<String,String>>()
        val parents=mutableMapOf<String,String>()
        override suspend fun send(method:String,path:String,body:JsonObject?):SharedHttpResponse {
            requests++;calls+="$method $path"
            fun ok(json:JsonObject)=SharedHttpResponse(200,json)
            if(path=="")return ok(buildJsonObject { put("private",private);put("default_branch","main") })
            if(method=="GET"&&path=="/git/ref/heads/main")return ok(buildJsonObject { put("object",buildJsonObject { put("sha",live) }) })
            if(method=="GET"&&path.startsWith("/git/commits/"))return ok(buildJsonObject { put("tree",buildJsonObject { put("sha",path.substringAfterLast('/')) }) })
            if(method=="GET"&&path.startsWith("/contents/")) {
                val ref=path.substringAfter("?ref=");val name=path.substringAfter("/contents/").substringBefore('?')
                val text=snapshots[ref]?.get(name) ?: return SharedHttpResponse(404,buildJsonObject {})
                return ok(buildJsonObject { put("type","file");put("encoding","base64");put("size",text.toByteArray().size);put("content",Base64.getEncoder().encodeToString(text.toByteArray())) })
            }
            if(method=="POST"&&path=="/git/blobs") {
                if(failBlob-->0)return SharedHttpResponse(503,buildJsonObject {})
                mutateAtBlob?.invoke();val text=body!!.getValue("content").jsonPrimitive.content;val hash=sharedHash(text);blobs[hash]=text
                return ok(buildJsonObject { put("sha",hash) })
            }
            if(method=="POST"&&path=="/git/trees") {
                val next=snapshots.getValue(body!!.getValue("base_tree").jsonPrimitive.content).toMutableMap()
                body.getValue("tree").jsonArray.forEach { entry ->
                    val row=entry.jsonObject;val key=row.getValue("path").jsonPrimitive.content;val sha=row["sha"]
                    if(sha==JsonNull)next.remove(key) else next[key]=blobs.getValue(sha!!.jsonPrimitive.content)
                }
                val hash="tree-${trees.size}";trees[hash]=next
                return ok(buildJsonObject { put("sha",hash) })
            }
            if(method=="POST"&&path=="/git/commits") {
                val hash="commit-${parents.size}";snapshots[hash]=trees.getValue(body!!.getValue("tree").jsonPrimitive.content)
                parents[hash]=body.getValue("parents").jsonArray.single().jsonPrimitive.content
                return ok(buildJsonObject { put("sha",hash) })
            }
            if(method=="PATCH") {
                patches++;if(race) { live="other";return SharedHttpResponse(422,buildJsonObject {}) }
                val sha=body!!.getValue("sha").jsonPrimitive.content
                assertFalse(body.getValue("force").jsonPrimitive.boolean)
                if(live!=sha&&parents[sha]!=live)return SharedHttpResponse(422,buildJsonObject {})
                live=sha
                if(dropPatchResponse)throw IOException("SECRET_MUST_NOT_APPEAR")
                return ok(buildJsonObject { put("object",buildJsonObject { put("sha",live) }) })
            }
            error("Unexpected request: $method $path")
        }
    }
    private val files=linkedMapOf("wo/days/2026-10-27.json" to "day","wo/memory.json" to "memory","wo/index.json" to "index")
    @Test fun recordsMemoryAndIndexBecomeVisibleInOneReferenceUpdate()=runBlocking {
        val git=Git();val store=GitHubSharedFiles(git,wait={})
        store.requirePrivate();store.commit(files,emptySet())
        assertEquals(files,git.snapshots[git.live]);assertEquals(1,git.patches)
        assertTrue(git.calls.filter { it.startsWith("GET /contents/") }.all { "?ref=initial" in it })
    }
    @Test fun transientBlobFailureRetriesButExhaustionLeavesLiveDataUnchanged()=runBlocking {
        val git=Git().apply { failBlob=1 };val waits=mutableListOf<Long>()
        GitHubSharedFiles(git,wait={waits+=it}).commit(files,emptySet())
        assertEquals(listOf(1000L),waits);assertEquals(files,git.snapshots[git.live])
        val broken=Git().apply { failBlob=10 }
        try { GitHubSharedFiles(broken,wait={}).commit(files,emptySet());fail() }
        catch(e:SharedSyncException) { assertTrue(e.retryable);assertTrue(e.message!!.contains("上传数据对象")) }
        assertEquals("initial",broken.live);assertEquals(0,broken.patches)
    }
    @Test fun concurrentWriterIsPreservedAndNeverForceUpdated()=runBlocking {
        val git=Git().apply { race=true }
        try { GitHubSharedFiles(git,wait={}).commit(files,emptySet());fail() }
        catch(e:SharedSyncException) { assertTrue(e.retryable) }
        assertEquals("other",git.live)
    }
    @Test fun lostSuccessfulPatchResponseIsConfirmedWithoutAnotherDataVersion()=runBlocking {
        val git=Git().apply { dropPatchResponse=true }
        GitHubSharedFiles(git,wait={}).commit(files,emptySet())
        assertEquals(files,git.snapshots[git.live]);assertEquals(1,git.parents.size)
    }
    @Test fun readsArePinnedToTheInitialCommit()=runBlocking {
        val git=Git();val store=GitHubSharedFiles(git,wait={});store.requirePrivate()
        git.snapshots["other"]=mapOf("wo/memory.json" to "new-memory");git.live="other"
        assertEquals("old-memory",store.read("wo/memory.json"))
    }
    @Test fun publicRepositoryOrChangedSnapshotCannotPublish()=runBlocking {
        val git=Git().apply { private=false }
        try { GitHubSharedFiles(git,wait={}).commit(files,emptySet());fail() }
        catch(e:SharedSyncException) { assertFalse(e.retryable) }
        assertTrue(git.blobs.isEmpty())
        val changing=Git();var active=true;changing.mutateAtBlob={active=false}
        try { GitHubSharedFiles(changing,active={active},wait={}).commit(files,emptySet());fail() }
        catch(e:SharedSyncException) { assertTrue(e.retryable) }
        assertEquals("initial",changing.live)
    }
    @Test fun unauthorizedRequestIsNotRetriedAndItsBodyIsNeverIncludedInError()=runBlocking {
        var requests=0
        val transport=SharedGitHubTransport { _,_,_->requests++;SharedHttpResponse(401,buildJsonObject { put("message","SECRET") }) }
        try { GitHubSharedFiles(transport,wait={}).requirePrivate();fail() }
        catch(e:SharedSyncException) { assertFalse(e.retryable);assertFalse(e.message!!.contains("SECRET")) }
        assertEquals(1,requests)
    }
}

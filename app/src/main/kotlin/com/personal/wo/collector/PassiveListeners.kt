package com.personal.wo.collector

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.*
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import com.personal.wo.wo
import com.personal.wo.recoverableCall
import com.personal.wo.recoverableTask
import com.personal.wo.core.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class NoticeListener : NotificationListenerService() {
    private val media = mutableMapOf<MediaSession.Token,Pair<MediaController,MediaController.Callback>>()
    private val playing = mutableSetOf<String>()
    private var connectedAt=0L
    private var sessionManager:MediaSessionManager?=null
    private var watch:Job?=null
    private var coverageJob:Job?=null
    private val noticeHashes=object:android.util.LruCache<String,String>(256){}
    private val listener=MediaSessionManager.OnActiveSessionsChangedListener { controllers -> syncMedia(controllers.orEmpty()) }
    private fun listen(block:suspend CoroutineScope.()->Unit):Job = wo().scope.launch {
        recoverableTask { block() }.onFailure { wo().diagnostics.record("通知与媒体监听",it) }
    }
    override fun onListenerConnected() {
        connectedAt=System.currentTimeMillis()
        listen { val g=wo().graph();g.repo.state(g.repo.source(Source.NOTIFICATION).copy(runtime="RUNNING")) }
        coverageJob?.cancel();coverageJob=listen {
            val r=wo().graph().repo;var previous=System.currentTimeMillis()
            while(isActive) { delay(300_000);val now=System.currentTimeMillis();val p=r.cachedPolicy
                if(p.requested&&!p.paused) { r.coverage(Source.NOTIFICATION,previous,now,true,"通知监听在线区间，不是完整消息获取率");r.coverage(Source.MEDIA,previous,now,true,"媒体会话观察在线区间") }
                previous=now
            }
        }
        recoverableCall {
            sessionManager=getSystemService(MediaSessionManager::class.java)
            val component=ComponentName(this,NoticeListener::class.java)
            sessionManager?.addOnActiveSessionsChangedListener(listener,component,Handler(Looper.getMainLooper()))
            syncMedia(sessionManager?.getActiveSessions(component).orEmpty())
        }.onFailure { wo().diagnostics.record("媒体会话连接",it) }
    }
    override fun onListenerDisconnected() {
        coverageJob?.cancel();coverageJob=null;closeMedia(); val end=System.currentTimeMillis()
        listen { val r=wo().graph().repo;r.coverage(Source.NOTIFICATION,connectedAt,end,true,"通知监听连接区间，不是消息获取率");r.state(r.source(Source.NOTIFICATION).copy(runtime="ERROR",detail="监听断开，期间通知可能缺失")) }
    }
    override fun onNotificationPosted(sbn:StatusBarNotification) {
        recoverableCall { recordNotice(sbn) }.onFailure { wo().diagnostics.record("通知元数据",it) }
    }
    private fun recordNotice(sbn:StatusBarNotification) {
        val graph=wo().currentGraph ?: return; val p=graph.repo.cachedPolicy
        if(!p.requested || p.paused || sbn.packageName==packageName || Source.NOTIFICATION !in p.enabledSources || sbn.packageName in p.excludedApps)return
        val n=sbn.notification
        if(n.flags and Notification.FLAG_GROUP_SUMMARY!=0 || n.extras.getInt(Notification.EXTRA_PROGRESS_MAX,0)>0 || n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false))return
        val allowed=sbn.packageName in p.noticeApps
        val (title,text)=if(allowed) NoticeRules.sanitize(n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()) else "通知片段" to ""
        val contentHash=graph.vault.sourceKey("notice-content",title,text,n.category)
        if(noticeHashes.get(sbn.key)==contentHash)return
        noticeHashes.put(sbn.key,contentHash)
        val key=graph.vault.sourceKey("notice",sbn.key,sbn.postTime,title,text)
        graph.offer(Observation(stableId(Source.NOTIFICATION,key),Source.NOTIFICATION,key,Kind.NOTICE,sbn.postTime,receivedMs=System.currentTimeMillis(),payload=Payload(packageName=sbn.packageName,label=title,text=text,category=NoticeRules.classify(title,text,allowed))))
    }
    override fun onNotificationRemoved(sbn:StatusBarNotification) { noticeHashes.remove(sbn.key) }
    private fun syncMedia(controllers:List<MediaController>) {
        recoverableCall { updateMedia(controllers) }.onFailure { wo().diagnostics.record("媒体会话查询",it) }
    }
    private fun updateMedia(controllers:List<MediaController>) {
        val tokens=controllers.map { it.sessionToken }.toSet()
        media.keys.filter { it !in tokens }.toList().forEach { token -> val pair=media.remove(token)!!;emitMedia(pair.first,false);pair.first.unregisterCallback(pair.second) }
        controllers.filter { it.sessionToken !in media }.forEach { controller ->
            val callback=object:MediaController.Callback() {
                override fun onPlaybackStateChanged(state:PlaybackState?) { emitMedia(controller,state?.state==PlaybackState.STATE_PLAYING) }
                override fun onMetadataChanged(metadata:MediaMetadata?) { if(controller.playbackState?.state==PlaybackState.STATE_PLAYING) { emitMedia(controller,false);emitMedia(controller,true) } }
                override fun onSessionDestroyed() { emitMedia(controller,false) }
            }
            controller.registerCallback(callback,Handler(Looper.getMainLooper()));media[controller.sessionToken]=controller to callback
            emitMedia(controller,controller.playbackState?.state==PlaybackState.STATE_PLAYING)
        }
        if(watch?.isActive!=true)watch=listen {
            val g=wo().graph()
            g.repo.changes.map { g.repo.cachedPolicy.let { it.requested&&!it.paused&&Source.MEDIA in it.enabledSources } }.distinctUntilChanged().collect { enabled ->
                withContext(Dispatchers.Main) { if(!enabled)playing.clear() else media.values.forEach { emitMedia(it.first,it.first.playbackState?.state==PlaybackState.STATE_PLAYING) } }
            }
        }
    }
    private fun emitMedia(controller:MediaController,isPlaying:Boolean) {
        recoverableCall { recordMedia(controller,isPlaying) }.onFailure { wo().diagnostics.record("媒体元数据",it) }
    }
    private fun recordMedia(controller:MediaController,isPlaying:Boolean) {
        val g=wo().currentGraph ?: return;val p=g.repo.cachedPolicy
        if(!p.requested || p.paused || Source.MEDIA !in p.enabledSources) { playing.clear();return }
        val session=controller.packageName+":"+controller.sessionToken.hashCode()
        val changed=if(isPlaying) playing.add(session) else playing.remove(session)
        if(!changed)return
        val now=System.currentTimeMillis();val key=g.vault.sourceKey("media",session,now,isPlaying)
        g.offer(Observation(stableId(Source.MEDIA,key),Source.MEDIA,key,if(isPlaying) Kind.MEDIA_PLAY else Kind.MEDIA_PAUSE,now,payload=Payload(packageName=controller.packageName,sessionKey=session,label=controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.take(256).orEmpty())))
    }
    private fun closeMedia() { media.values.forEach { (c,callback) -> recoverableCall { emitMedia(c,false);c.unregisterCallback(callback) } };media.clear();recoverableCall { sessionManager?.removeOnActiveSessionsChangedListener(listener) } }
    override fun onDestroy() { watch?.cancel();watch=null;coverageJob?.cancel();coverageJob=null;closeMedia();super.onDestroy() }
}

class WindowListener : AccessibilityService() {
    private var lastPackage="";private var lastTime=0L
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return
        val pkg=event.packageName?.toString() ?: return; val now=System.currentTimeMillis()
        if(pkg==lastPackage && now-lastTime<500)return
        lastPackage=pkg;lastTime=now
        val g=wo().currentGraph ?: return
        if(Source.ACCESSIBILITY !in g.repo.cachedPolicy.enabledSources)return
        val key=g.vault.sourceKey("window",pkg,now)
        g.offer(Observation(stableId(Source.ACCESSIBILITY,key),Source.ACCESSIBILITY,key,Kind.WINDOW,now,payload=Payload(packageName=pkg,label=pkg)))
    }
    override fun onInterrupt() = Unit
}

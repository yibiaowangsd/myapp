package com.personal.wo.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.wo.*
import com.personal.wo.core.*
import com.personal.wo.data.*
import com.personal.wo.collector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class WoUiState(
    val loaded:Boolean=false,val busy:Boolean=false,val message:String?=null,val startupError:String?=null,val policy:Policy=Policy(),
    val day:DayView?=null,val history:List<DayView> = emptyList(),val sources:List<SourceState> = emptyList(),
    val episode:Episode?=null,val results:List<Episode> = emptyList(),val places:List<Place> = emptyList(),
    val insights:List<Insight> = emptyList(),val recoveryCode:String?=null,val inspection:RestoreInspection?=null,
    val selectedDate:String=LocalDate.now().toString(),val demo:Boolean=false,val databaseBytes:Long=0,
    val diagnostics:List<String> = emptyList(),val positions:List<Observation> = emptyList()
)
class WoViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as WoApplication
    private val mutable=MutableStateFlow(WoUiState())
    val state=mutable.asStateFlow()
    private var loadJob:Job?=null
    private var searchJob:Job?=null
    private var initializationJob:Job?=null
    private var foregroundJob:Job?=null
    private var searchText="";private var searchAll=false
    @Volatile private var visible=true
    init { initialize() }
    private fun initialize() {
        initializationJob?.cancel()
        initializationJob=viewModelScope.launch(Dispatchers.IO) {
            recoverableTask {
                val g=app.graph();g.collector.probe()
                val zone=ZoneId.of(g.repo.cachedPolicy.archiveZone);mutable.update { it.copy(policy=g.repo.cachedPolicy,selectedDate=LocalDate.now(zone).toString()) }
                reload(false)
                g.repo.changes.debounce(500).collect { if(visible)reload(false) }
            }.onFailure { failure("载入首页",it) }
        }
    }
    private fun failure(stage:String,error:Throwable) {
        val report=app.diagnostics.record(stage,error)
        mutable.update { if(it.day==null)it.copy(loaded=true,busy=false,startupError=report)
            else it.copy(busy=false,message="本次操作未完成（${error.javaClass.simpleName}），已有记录保留") }
    }
    fun retryStartup() {
        loadJob?.cancel();foregroundJob?.cancel()
        mutable.update { it.copy(loaded=false,busy=false,startupError=null) }
        app.retryInitialization();initialize();foreground()
    }
    private suspend fun reload(pull:Boolean) {
        if(mutable.value.demo)return
        val g=app.graph();val date=LocalDate.parse(mutable.value.selectedDate)
        val cached=g.repo.day(date,false)
        mutable.update { it.copy(day=cached,loaded=true,startupError=null,policy=g.repo.cachedPolicy) }
        if(pull) { g.collector.probe();withTimeoutOrNull(20_000) { g.collector.pull(true) } }
        val day=g.repo.day(date);val history=g.repo.history();val sources=g.repo.sources();val places=g.repo.places();val ops=g.db.dao().operations()
        val positions=g.db.dao().observations(day.windowStartMs,day.windowEndMs).filter { it.sourceId==Source.LOCATION.name }.map { WoJson.decodeFromString<Observation>(it.json) }.flatMap { filterObservation(it,g.repo.cachedPolicy) }
        mutable.update { it.copy(positions=positions) }
        mutable.update { it.copy(day=if(it.selectedDate==date.toString()) day else it.day,history=history,policy=g.repo.cachedPolicy,sources=sources,places=places,insights=insights(history),databaseBytes=app.getDatabasePath("wo.db").length()+app.getDatabasePath("wo.db-wal").length(),diagnostics=ops.map { o -> "${Instant.ofEpochMilli(o.timeMs).atZone(ZoneId.of(g.repo.cachedPolicy.archiveZone)).toLocalTime().withNano(0)} · ${o.code}${if(o.count>0) " · ${o.count}" else ""}" }) }
    }
    fun refresh(pull:Boolean=true) { loadJob?.cancel();loadJob=viewModelScope.launch(Dispatchers.IO) { mutable.update { it.copy(busy=true) };try { recoverableTask { reload(pull) }.onFailure { failure("刷新日视图",it) } } finally { mutable.update { it.copy(busy=false) } } } }
    fun foreground() {
        visible=true
        foregroundJob?.cancel()
        foregroundJob=viewModelScope.launch(Dispatchers.IO) { recoverableTask { val g=app.graph();g.collector.probe();if(g.repo.cachedPolicy.requested&&!g.repo.cachedPolicy.paused) {
            if(g.repo.cachedPolicy.lastAliveMs>0&&System.currentTimeMillis()-g.repo.cachedPolicy.lastAliveMs>45*60_000)g.repo.coverage(Source.NOTIFICATION,g.repo.cachedPolicy.lastAliveMs,System.currentTimeMillis(),false,"监听状态可能中断；未收到不代表没有消息")
            RecordingService.start(app);if(!WorkScheduling.schedule(app))message("系统后台调度暂不可用，打开应用时仍会同步")
        } }.onFailure { failure("前台恢复",it) } }
        refresh(true)
    }
    fun background() { visible=false;loadJob?.cancel();searchJob?.cancel();foregroundJob?.cancel() }
    fun date(date:LocalDate) { mutable.update { it.copy(selectedDate=date.toString(),demo=false) };refresh(true) }
    fun message(value:String?) { mutable.update { it.copy(message=value) } }
    private fun action(block:suspend(Graph)->Unit) { viewModelScope.launch(Dispatchers.IO) {
        mutable.update { it.copy(busy=true) }
        try { recoverableTask { block(app.graph());reload(false) }.onFailure { if(it is LinkageError||app.currentGraph==null)failure("记录操作",it) else message(it.message ?: "操作未完成，请重试") } } finally { mutable.update { it.copy(busy=false) } }
    } }
    fun recording(action:String) { mutable.update { it.copy(demo=false) };action { g ->
        g.repo.command(action)
        if(action=="pause"||action=="stop") { app.stopService(Intent(app,RecordingService::class.java));WorkScheduling.pause(app) }
        else { if(!WorkScheduling.schedule(app))message("系统后台调度暂不可用，打开应用时仍会同步");RecordingService.start(app);g.collector.probe();g.collector.pull(true) }
    } }
    fun beginRecording(sources:Set<Source>) {
        mutable.update { it.copy(demo=false) }
        action { g ->
            g.repo.configure { it.copy(enabledSources=sources) }
            g.repo.command(if(g.repo.cachedPolicy.paused) "resume" else "start")
            g.collector.probe()
            app.stopService(Intent(app,RecordingService::class.java))
            if(!WorkScheduling.schedule(app))message("系统后台调度暂不可用，打开应用时仍会同步")
            RecordingService.start(app)
            g.collector.pull(true)
        }
    }
    fun permissionsChanged()=action { g ->
        g.collector.probe()
        if(g.repo.cachedPolicy.requested&&!g.repo.cachedPolicy.paused) {
            // A permission return can follow onResume's probe; rebuild active listeners
            // even when the persisted authorization state has already been refreshed.
            app.stopService(Intent(app,RecordingService::class.java))
            RecordingService.start(app)
            g.collector.pull(true)
        }
    }
    fun source(source:Source,on:Boolean)=action { g ->
        g.repo.configure { it.copy(enabledSources=if(on)it.enabledSources+source else it.enabledSources-source) }
        app.stopService(Intent(app,RecordingService::class.java))
        if(g.repo.cachedPolicy.requested&&!g.repo.cachedPolicy.paused)RecordingService.start(app)
    }
    fun noticeApps(packages:Set<String>)=action { g -> g.repo.configure { it.copy(noticeApps=packages.map { s -> s.trim() }.filter { it.isNotEmpty() }.toSet()) } }
    fun excludeApps(packages:Set<String>)=action { g -> g.repo.configure { it.copy(excludedApps=packages.map { s -> s.trim() }.filter { it.isNotEmpty() }.toSet()) } }
    fun excludeRange(start:Long,end:Long)=action { g -> require(end>start);g.repo.configure { it.copy(exclusions=it.exclusions+Exclusion(java.util.UUID.randomUUID().toString(),start,end,reason="USER_RANGE")) };message("指定时段已排除，补采也会过滤此范围") }
    fun lock(on:Boolean)=action { g -> g.repo.configure { it.copy(lockUi=on) } }
    fun enhancedConfig(endpoint:String,model:String,key:String)=action { g -> val service=EnhancedSummary(g.repo);val old=service.config();service.save(EnhancedConfig(endpoint,key.ifBlank { old.apiKey },model));message("增强配置已保存在本机，访问密钥已加密") }
    fun enhance() { viewModelScope.launch(Dispatchers.IO) { val day=mutable.value.day ?: return@launch;try { val sentences=EnhancedSummary(app.graph().repo).arrange(day);mutable.update { if(it.day?.revision==day.revision&&it.day?.dayDate==day.dayDate)it.copy(day=day.copy(sentences=sentences)) else it };message("已按已知事实调整摘要顺序") }catch(e:Exception){message(e.message ?: "增强未完成，保留本地摘要")} } }
    fun episode(id:String) { action { g -> mutable.update { it.copy(episode=if(it.demo)it.day?.episodes?.firstOrNull { e -> e.id==id } else g.repo.episode(id)) } } }
    fun annotate(title:String,confirmed:Boolean,start:Long?=null,end:Long?=null)=action { g -> val e=mutable.value.episode ?: return@action;g.repo.annotate(e,title,confirmed,start=start,end=end);mutable.update { it.copy(episode=g.repo.episode(e.id)) };message("片段已更新") }
    fun favorite()=action { g -> val e=mutable.value.episode ?: return@action;g.repo.annotate(e,null,false,!e.isFavorite);mutable.update { it.copy(episode=g.repo.episode(e.id)) } }
    fun namePlace(name:String)=action { g -> val e=mutable.value.episode ?: return@action;g.repo.namePlace(e,name);message("地点已命名，后续停留将自动匹配") }
    fun deleteEpisode()=action { g -> val e=mutable.value.episode ?: return@action;val p=g.repo.preview(e.startMs,maxOf(e.endMs,e.startMs+1),e.id);g.repo.delete(p);mutable.update { it.copy(episode=null,results=it.results.filterNot { r->r.id==e.id }) };message("片段和相关依据已删除，并阻止再次导入") }
    fun deleteDay()=action { g -> val d=mutable.value.day ?: return@action;g.repo.delete(g.repo.preview(d.windowStartMs,d.windowEndMs));mutable.update { it.copy(results=it.results.filterNot { e->e.startMs<d.windowEndMs&&e.endMs>=d.windowStartMs }) };message("这一天的记录已删除，并阻止历史补采") }
    fun search(text:String,all:Boolean=false) { searchText=text;searchAll=all;searchJob?.cancel();searchJob=viewModelScope.launch(Dispatchers.IO) { recoverableTask { delay(300);val r=app.graph().repo.search(text,LocalDate.now().minusDays(90),all);mutable.update { it.copy(results=r) } }.onFailure { failure("搜索",it) } } }
    fun moreSearch() { if(mutable.value.results.size>=200)return;searchJob?.cancel();searchJob=viewModelScope.launch(Dispatchers.IO) { recoverableTask { val r=app.graph().repo.search(searchText,LocalDate.now().minusDays(90),searchAll,mutable.value.results.size);mutable.update { it.copy(results=(it.results+r).distinctBy { e->e.id }.take(200)) } }.onFailure { failure("搜索分页",it) } } }
    fun export(uri:Uri,archive:Boolean,json:Boolean=false)=action { g ->
        if(archive) { val code=BackupService(app,g.repo).export(uri);mutable.update { it.copy(recoveryCode=code) };message("加密备份已完成，请单独保存恢复码") }
        else { BackupService(app,g.repo).exportDay(uri,mutable.value.day ?: return@action,json);message("日摘要已导出") }
    }
    fun inspect(uri:Uri,code:String)=action { g -> val inspection=BackupService(app,g.repo).inspect(uri,code);mutable.update { it.copy(inspection=inspection) };message("认证与内容校验通过，可确认恢复") }
    fun cancelRestore() { mutable.value.inspection?.directory?.deleteRecursively();mutable.update { it.copy(inspection=null) } }
    fun restore()=action { g ->
        val i=mutable.value.inspection ?: return@action
        g.repo.command("stop");app.stopService(Intent(app,RecordingService::class.java));WorkScheduling.pause(app)
        BackupService(app,g.repo).restore(i);g.repo.initialize();g.collector.probe()
        mutable.update { it.copy(inspection=null,selectedDate=LocalDate.now(ZoneId.of(g.repo.cachedPolicy.archiveZone)).toString()) }
        message("记录已恢复。请重新确认来源授权，再开始记录；照片需要重新关联")
    }
    fun demo(on:Boolean) {
        if(!on) { mutable.update { it.copy(demo=false) };refresh(false);return }
        val zone=mutable.value.policy.archiveZone
        val today=LocalDate.now(ZoneId.of(zone))
        mutable.update { it.copy(demo=true,day=demoDay(today,zone),selectedDate=today.toString()) }
    }

    override fun onCleared() { mutable.value.inspection?.directory?.deleteRecursively();super.onCleared() }
}

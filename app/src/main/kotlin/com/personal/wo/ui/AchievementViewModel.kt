package com.personal.wo.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.wo.WoApplication
import com.personal.wo.core.*
import com.personal.wo.data.AchievementStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AchievementUiState(val snapshot:AchievementSnapshot?=null,val loading:Boolean=false,val error:String?=null)

/** Lifecycle-bound work only. No sensor, alarm, background worker or extra network request. */
class AchievementViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as WoApplication
    private val mutable=MutableStateFlow(AchievementUiState())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    private var epoch=0L
    private var demo=false
    private var active=false
    private var ready=false
    private var lastScan=Long.MIN_VALUE
    private var sample:DayView?=null
    private var policy=Policy()

    fun sync(s:WoUiState,visible:Boolean) {
        val modeChanged=demo!=s.demo
        val entering=visible&&!active
        ready=s.loaded;active=visible;demo=s.demo;sample=s.day;policy=s.policy
        if(modeChanged){epoch++;job?.cancel();mutable.value=AchievementUiState();lastScan=Long.MIN_VALUE}
        if(!visible){job?.cancel();mutable.update{it.copy(loading=false)};return}
        if(!ready)return
        val now=SystemClock.elapsedRealtime()
        if(modeChanged||entering||lastScan==Long.MIN_VALUE||now-lastScan>=15_000L)refresh()
    }
    fun refresh() {
        if(!active||!ready)return
        job?.cancel()
        val generation=epoch
        val sampleDay=sample
        mutable.update{it.copy(loading=true,error=null)}
        job=viewModelScope.launch(Dispatchers.IO) {
            try {
                val result=if(demo)achievementSnapshot(listOfNotNull(sampleDay),policy=Policy(archiveZone=policy.archiveZone),demo=true)
                else AchievementStore(app.graph().repo).load()
                ensureActive()
                if(generation==epoch){lastScan=SystemClock.elapsedRealtime();mutable.value=AchievementUiState(result)}
            } catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation==epoch)mutable.update{it.copy(loading=false,error="成就暂未更新：${e.message?.take(100)?:"请重试"}")}}
        }
    }
    private fun action(block:suspend(AchievementStore)->Unit) {
        if(demo||!ready||!active)return
        if(job?.isActive==true)return
        val generation=epoch
        mutable.update{it.copy(loading=true,error=null)}
        job=viewModelScope.launch(Dispatchers.IO) {
            try {
                val store=AchievementStore(app.graph().repo)
                block(store)
                val result=store.load();ensureActive()
                if(generation==epoch){lastScan=SystemClock.elapsedRealtime();mutable.value=AchievementUiState(result)}
            } catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation==epoch)mutable.update{it.copy(loading=false,error=e.message?.take(140)?:"成就操作未完成，请重试")}}
        }
    }
    fun create(goal:AchievementGoal)=action{it.add(goal)}
    fun delete(id:String)=action{it.remove(id)}
    fun reading(packages:Set<String>)=action{it.readingApps(packages)}
    fun showcase(id:String)=action {
        require(mutable.value.snapshot?.unlocked?.any{p->p.goal.id==id}==true){"达成后才能放入展台"}
        it.showcase(id)
    }
    fun seen(ids:Set<String>)=action{it.acknowledge(ids)}
}

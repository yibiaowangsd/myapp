package com.personal.wo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.wo.WoApplication
import com.personal.wo.collector.SharedDataWork
import com.personal.wo.data.SharedDataSync
import com.personal.wo.data.SharedSyncStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SharedDataUiState(
    val loaded:Boolean=false,val busy:Boolean=false,val repository:String="",val enabled:Boolean=false,
    val hasToken:Boolean=false,val status:SharedSyncStatus=SharedSyncStatus(),val error:String?=null
)
class SharedDataViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as WoApplication
    private val mutable=MutableStateFlow(SharedDataUiState())
    val state=mutable.asStateFlow()
    private suspend fun refreshState(service:SharedDataSync) {
        val config=service.config();val status=service.status()
        mutable.update { it.copy(loaded=true,repository=config.repository,enabled=config.enabled,hasToken=config.token.isNotBlank(),status=status) }
    }
    fun refresh() { if(mutable.value.busy)return;viewModelScope.launch(Dispatchers.IO) {
        try { refreshState(app.graph().sharedData) }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { mutable.update { it.copy(loaded=true,error="配置暂时无法读取，请重试") } }
    } }
    fun saveAndSync(repository:String,token:String)=operation { service ->
        service.configure(repository,token,true)
        SharedDataWork.schedule(app,true)
        refreshState(service)
        service.sync()
    }
    fun sync()=operation { it.sync() }
    fun pause()=operation { service ->
        val config=service.config();service.configure(config.repository,"",false)
        SharedDataWork.schedule(app,false)
    }
    private fun operation(action:suspend(SharedDataSync)->Unit) {
        if(mutable.value.busy)return
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch(Dispatchers.IO) {
            try { val service=app.graph().sharedData;action(service);refreshState(service) }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                runCatching { refreshState(app.graph().sharedData) }
                mutable.update { it.copy(error=if(e is com.personal.wo.data.SharedSyncException)e.message else "同步暂时未完成，请重试") }
            }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
}

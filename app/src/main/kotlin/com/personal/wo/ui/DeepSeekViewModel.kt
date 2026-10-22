package com.personal.wo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.wo.WoApplication
import com.personal.wo.collector.DeepSeekWork
import com.personal.wo.core.LifeMemory
import com.personal.wo.data.DeepSeekService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DeepSeekUiState(
    val loaded:Boolean=false,val busy:Boolean=false,val enabled:Boolean=false,val autoReview:Boolean=false,
    val model:String="deepseek-flash",val hasKey:Boolean=false,val memory:LifeMemory=LifeMemory(),val error:String?=null
)
class DeepSeekViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as WoApplication
    private val mutable=MutableStateFlow(DeepSeekUiState())
    val state=mutable.asStateFlow()
    private suspend fun refreshState(service:DeepSeekService) {
        val config=service.config();val memory=service.memory.read()
        mutable.update { it.copy(loaded=true,enabled=config.enabled,autoReview=config.autoReview,model=config.model,hasKey=config.apiKey.isNotBlank(),memory=memory) }
    }
    fun refresh()=operation { }
    fun saveAndReview(key:String,model:String,automatic:Boolean)=operation { service ->
        service.configure(key,model,true,automatic)
        DeepSeekWork.schedule(app,automatic);refreshState(service)
        service.review()
    }
    fun review()=operation { it.review() }
    fun pause()=operation { service ->
        val config=service.config();service.configure("",config.model,false,config.autoReview)
        DeepSeekWork.schedule(app,false)
    }
    fun saveNotes(notes:String)=operation { it.memory.saveNotes(notes) }
    fun forget()=operation { it.memory.clearAll() }
    private fun operation(action:suspend(DeepSeekService)->Unit) {
        if(mutable.value.busy)return
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch(Dispatchers.IO) {
            val service=try { app.graph().deepSeek } catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.update { it.copy(busy=false,error="本地组件暂时不可用，请重试") };return@launch }
            try { action(service) }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.update { it.copy(error=when(e) {
                is IllegalArgumentException,is IllegalStateException->e.message ?: "结果格式不正确，本次未更新记忆"
                else->"DeepSeek 请求未完成，请检查网络后重试；已有记忆已保留"
            }) } }
            finally {
                try { refreshState(service) } catch(e:CancellationException) { throw e } catch(_:Exception) { }
                mutable.update { it.copy(busy=false) }
            }
        }
    }
}

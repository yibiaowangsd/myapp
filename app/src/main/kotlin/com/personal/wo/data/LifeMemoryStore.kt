package com.personal.wo.data

import androidx.room.withTransaction
import com.personal.wo.core.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal const val LIFE_MEMORY_KEY="life-memory.v1"
internal suspend fun WoDao.lifeMemory():LifeMemory=meta(LIFE_MEMORY_KEY)?.value?.let { WoJson.decodeFromString<LifeMemory>(it).checked() } ?: LifeMemory()
internal suspend fun WoDao.putLifeMemory(memory:LifeMemory) { putMeta(MetaRow(LIFE_MEMORY_KEY,WoJson.encodeToString(memory.checked()))) }
/** Called inside the repository mutation transaction, including when a request is still in flight. */
internal suspend fun WoDao.invalidateLifeMemory(affected:(String)->Boolean={true}) {
    val old=lifeMemory()
    putLifeMemory(old.copy(observations=old.observations.filterNot { it.dates.any(affected) },
        review=old.review?.takeUnless { it.dates.any(affected) },revision=old.revision+1,updatedAtMs=System.currentTimeMillis()))
}

class LifeMemoryStore(private val repo:WoRepository) {
    suspend fun read():LifeMemory=repo.mutex.withLock { repo.db.dao().lifeMemory() }
    suspend fun saveNotes(notes:String):LifeMemory=change { old ->
        require(notes.length<=4000) { "偏好与目标最多4000字" }
        old.withoutDerived().copy(notes=notes.trim())
    }
    suspend fun clearDerived():LifeMemory=change { it.withoutDerived() }
    suspend fun clearAll():LifeMemory=change { LifeMemory(revision=it.revision) }
    suspend fun commit(next:LifeMemory,expectedRevision:Long,expectedGeneration:Long):LifeMemory=change { old ->
        check(old.revision==expectedRevision&&repo.cachedPolicy.generation==expectedGeneration) { "记录或记忆已变化，本次结果未保存，请重新生成" }
        next
    }
    private suspend fun change(transform:(LifeMemory)->LifeMemory):LifeMemory=repo.mutex.withLock {
        val result=repo.db.withTransaction {
            val old=repo.db.dao().lifeMemory()
            transform(old).copy(revision=old.revision+1,updatedAtMs=System.currentTimeMillis()).checked().also { repo.db.dao().putLifeMemory(it) }
        }
        repo.memoryChanged();result
    }
}

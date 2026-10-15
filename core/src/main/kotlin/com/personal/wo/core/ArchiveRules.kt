package com.personal.wo.core

object ArchiveRules {
    val names=setOf("manifest.json","observations.jsonl","episodes.jsonl","annotations.jsonl","places.json","policy.json","daily.jsonl")
    const val maxExpandedBytes=512L*1024*1024
    fun entry(name:String,isDirectory:Boolean,seen:MutableSet<String>) {
        require(!isDirectory&&name in names&&seen.add(name)) { "归档条目不在允许名单或重复" }
    }
    fun header(length:Int,version:Int=1,algorithm:String="AES256_GCM_HKDF_1MB") {
        require(length in 1..65536&&version==1&&algorithm=="AES256_GCM_HKDF_1MB")
    }
}

package com.personal.wo.collector

import android.content.Context
import android.content.Intent
import com.personal.wo.core.*

data class ConnectedApp(val packageName:String,val label:String,val group:String="其他应用")

object AppConnections {
    private val music=setOf("com.tencent.qqmusic","com.netease.cloudmusic","com.android.mediacenter","com.huawei.music","com.kugou.android","cn.kuwo.player","com.spotify.music","com.ximalaya.ting.android")
    private val travel=setOf("com.autonavi.minimap","com.baidu.BaiduMap","com.sdu.didi.psnger","ctrip.android.view","com.MobileTicket","com.Qunar","com.huawei.maps.app")
    private val life=setOf("com.sankuai.meituan","me.ele","com.taobao.taobao","com.jingdong.app.mall","com.eg.android.AlipayGphone","com.tencent.mm","com.tencent.mobileqq")
    fun group(pkg:String)=when(pkg) {
        in music->"音乐与播客"
        in travel->"出行与地图"
        in life->"生活与消息"
        "com.huawei.health","com.xiaomi.wearable","com.xiaomi.hm.health","com.sec.android.app.shealth"->"运动与健康"
        else->"其他应用"
    }
    fun load(c:Context,known:Set<String>):List<ConnectedApp> {
        val pm=c.packageManager
        // Launcher visibility only, plus applications already present in the user's local records.
        val packages=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .map { it.activityInfo.packageName }.toSet()+known
        return packages.asSequence().filter { it!=c.packageName }.mapNotNull { pkg ->
            runCatching { val info=pm.getApplicationInfo(pkg,0);ConnectedApp(pkg,pm.getApplicationLabel(info).toString(),group(pkg)) }.getOrNull()
        }.distinctBy { it.packageName }.sortedWith(compareBy<ConnectedApp> { it.group=="其他应用" }.thenBy { it.group }.thenBy { it.label }).toList()
    }
}

fun connectionStatus(app:ConnectedApp,p:Policy,sources:List<SourceState>):String {
    if(app.packageName in p.excludedApps)return "已排除"
    if(!p.requested)return "待开始记录"
    if(p.paused)return "记录已暂停"
    val usage=Source.USAGE in p.enabledSources&&sourceReady(sources.firstOrNull { it.source==Source.USAGE })
    val notice=app.packageName in p.noticeApps&&Source.NOTIFICATION in p.enabledSources&&sourceReady(sources.firstOrNull { it.source==Source.NOTIFICATION })
    return when { usage&&notice->"使用时段 · 通知线索";notice->"通知线索已开启";usage->"使用时段已开启";else->"待开启来源权限" }
}

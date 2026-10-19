package com.personal.wo.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.*

@Serializable enum class AchievementMetric(val label:String,val unit:String,val note:String) {
    DAYS("时光收藏","天","有有效生活片段或有效步数的归档日；不是打开本应用或签到次数。"),
    STREAK("日常相伴","天","当前保留归档中，曾经连续有记录的最长天数。中断不会抹去已完成的连续记录。"),
    STEPS("步履不停","步","累计已保存的日步数。沿用原有步数合并结果，不叠加手机与健康来源的同一天步数。"),
    EXERCISE("活力时刻","分钟","健康来源已记录或本人确认的运动片段，重叠时间只计算一次；地点移动不视为运动。"),
    PHOTOS("光影收藏","项","已经关联到时间线的影像，按媒体标识去重；不代表全部相册，也不判断照片内容。"),
    READING("阅读陪伴","分钟","所选阅读应用的已观察前台时长，仅作近似；不等于有效阅读时长、读完书籍数或微信读书官方统计。"),
    PLACES("生活足迹","处","本人确认、至少停留10分钟且有定位依据的地点；距已计入地点300米内不重复计数。"),
    FAVORITES("值得珍藏","段","当前收藏的有效生活片段，按事件标识去重；取消收藏或删除记录后同步重算。")
}

@Serializable data class AchievementGoal(
    val id:String,val name:String,val description:String,val metric:AchievementMetric,
    val target:Long,val tier:Int=1,val custom:Boolean=false
) {
    fun checked():AchievementGoal {
        require(name.isNotBlank()&&name.length<=28){"成就名称需为1至28个字"}
        require(description.length<=180){"描述最多180个字"}
        require(tier in 1..4&&id.matches(Regex("[a-z0-9_-]{1,80}"))){"成就标识无效"}
        val maximum=when(metric){AchievementMetric.DAYS,AchievementMetric.STREAK->36500L;AchievementMetric.STEPS->1_000_000_000L;AchievementMetric.EXERCISE,AchievementMetric.READING->10_000_000L;else->1_000_000L}
        require(target in 1..maximum){"目标需为1至$maximum${metric.unit}"}
        return copy(name=name.trim(),description=description.trim())
    }
    val rule:String get()=when(metric){
        AchievementMetric.STREAK->"曾连续记录 $target 天"
        AchievementMetric.FAVORITES->"收藏 $target 段生活片段"
        AchievementMetric.PLACES->"确认 $target 处不同停留地点"
        else->"累计${when(metric){AchievementMetric.DAYS->"记录";AchievementMetric.STEPS->"步行";AchievementMetric.EXERCISE->"运动";AchievementMetric.PHOTOS->"关联影像";AchievementMetric.READING->"阅读应用前台使用";else->""}} $target ${metric.unit}"
    }
}

val achievementCatalog:List<AchievementGoal> = buildList {
    fun series(metric:AchievementMetric,targets:List<Long>,names:List<String>,descriptions:List<String>) {
        targets.forEachIndexed { index,target->add(AchievementGoal("${metric.name.lowercase()}-${index+1}",names[index],descriptions[index],metric,target,index+1)) }
    }
    series(AchievementMetric.DAYS,listOf(1,7,30,100),listOf("故事的第一页","一周的切片","日常成册","百日有痕"),
        listOf("平凡的一天，也值得留下。","七个日子，拼起生活的轮廓。","那些看似相同的日子，各有自己的光。","一百个有记录的日子，写成独属于你的故事。"))
    series(AchievementMetric.STREAK,listOf(3,7,14,30),listOf("三日相伴","一周不断线","两周的节奏","日常有回声"),
        listOf("连续三天，把日常轻轻接在一起。","连续一周，生活的细节有了回声。","十四个相邻的日子，留下自己的节奏。","曾经连续三十天，认真看见自己的生活。"))
    series(AchievementMetric.STEPS,listOf(1000,10000,100000,1000000),listOf("迈出一步","万步成途","走过十万步","步履成诗"),
        listOf("路不必很远，出发就有意义。","一万步，是许多个小小的出发。","走过的路，已经悄悄累积。","百万步不是终点，是一路走来的印记。"))
    series(AchievementMetric.EXERCISE,listOf(10,60,300,1000),listOf("唤醒活力","动起来的一小时","五小时的热爱","活力收藏家"),
        listOf("给身体留下一小段活动的时间。","一点点活动，汇成一小时的活力。","累计五小时，为生活添一些舒展。","一千分钟，收集属于自己的运动时刻。"))
    series(AchievementMetric.PHOTOS,listOf(1,10,50,200),listOf("留住这一刻","十帧日常","光影成集","生活摄影册"),
        listOf("一张影像，让这一刻有了形状。","十个被留下的画面，十次与日常相遇。","五十个光影片段，组成一份生活收藏。","两百项影像，让回忆有迹可循。"))
    series(AchievementMetric.READING,listOf(30,120,600,3000),listOf("翻开一页","与书相处","书页之间","长久的陪伴"),
        listOf("为阅读留出一段时间，也为自己留一片安静。","与阅读应用相处的时间，慢慢有了分量。","十小时的阅读陪伴，留给好奇心。","五十小时，把翻页变成生活的一部分。"))
    series(AchievementMetric.PLACES,listOf(1,3,10,30),listOf("在此停留","生活的三点","十处足迹","我的生活地图"),
        listOf("确认一处停留，把生活放回真实的位置。","三处不同的停留，勾勒日常的范围。","十处亲自确认的足迹，收藏一路的相遇。","三十处停留，不论远近，都属于你的生活。"))
    series(AchievementMetric.FAVORITES,listOf(1,5,20,50),listOf("一颗小星星","五份偏爱","回忆匣子","珍藏自己的光"),
        listOf("亲手收藏一段值得回看的日常。","五份偏爱，替你记住生活的温柔。","把二十段回忆，收进自己的小匣子。","五十段珍藏，都是你亲自选中的光。"))
}

@Serializable data class AchievementSettings(
    val version:Int=1,val readingPackages:Set<String> = setOf("com.tencent.weread"),
    val customGoals:List<AchievementGoal> = emptyList(),val showcaseIds:List<String> = emptyList()
) {
    fun checked():AchievementSettings {
        require(version==1){"成就设置版本暂不支持"}
        require(readingPackages.size<=32&&readingPackages.all{it.length<=200&&it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))}){"阅读应用配置无效"}
        require(customGoals.size<=12&&customGoals.map{it.id}.distinct().size==customGoals.size){"最多保留12个自定义成就"}
        customGoals.forEach { require(it.custom&&it.id.startsWith("custom-"));it.checked() }
        require(showcaseIds.size<=3&&showcaseIds.distinct().size==showcaseIds.size){"展台最多放3枚徽章"}
        require(showcaseIds.all{id->(achievementCatalog+customGoals).any{it.id==id}}){"展台包含不存在的成就"}
        return this
    }
}
@Serializable data class AchievementMemory(val version:Int=1,val seenIds:Set<String> = emptySet())
data class AchievementProgress(val goal:AchievementGoal,val current:Long,val earnedOn:String?,val hasEvidence:Boolean) {
    val unlocked get()=earnedOn!=null&&current>=goal.target
    val fraction get()=(current.toDouble()/goal.target).coerceIn(0.0,1.0).toFloat()
}
data class AchievementSnapshot(
    val items:List<AchievementProgress> = emptyList(),val settings:AchievementSettings=AchievementSettings(),
    val newIds:Set<String> = emptySet(),val fromDate:String="",val toDate:String="",val demo:Boolean=false
) { val unlocked get()=items.filter{it.unlocked} }

@Serializable data class AchievementPlace(val lat:Double,val lon:Double)
/** Minimal per-day evidence, retained inside SQLCipher; cross-day deduplication stays in the accumulator. */
@Serializable data class AchievementDayContribution(
    val date:String,val zone:String,val recorded:Boolean,val steps:Long?,
    val exerciseMs:Long,val readingMs:Long,val exerciseObserved:Boolean,val readingObserved:Boolean,
    val photoIds:List<String>,val favoriteIds:List<String>,val favoriteObserved:Boolean,
    val places:List<AchievementPlace>
)

/** Read-only streaming projection over chronological archive days; no raw collection or clock timer. */
class AchievementAccumulator(private val settings:AchievementSettings,private val policy:Policy) {
    private val goals=achievementCatalog+settings.checked().customGoals
    private val values=AchievementMetric.entries.associateWith{0L}.toMutableMap()
    private val observed=mutableSetOf<AchievementMetric>()
    private val earned=mutableMapOf<String,String>()
    private val photos=mutableSetOf<String>()
    private val favorites=mutableSetOf<String>()
    private val places=mutableListOf<Pair<Double,Double>>()
    private var previousDate:LocalDate?=null
    private var previousRecorded:LocalDate?=null
    private var streak=0L
    private var exerciseMs=0L
    private var readingMs=0L

    private fun blocked(e:Episode,x:Exclusion):Boolean =
        (x.packageName==null||x.packageName==e.packageName)&&
        (x.source==null||e.evidence.any{it.source==x.source})&&
        (x.sourceKey==null||e.evidence.any{it.sourceKey==x.sourceKey&&(x.source==null||it.source==x.source)})
    private fun spans(e:Episode,d:DayView):List<Span> {
        val start=maxOf(e.startMs,d.windowStartMs);val end=minOf(e.endMs,d.windowEndMs,d.asOfMs)
        if(end<=start)return emptyList()
        var spans=listOf(Span(start,end))
        policy.exclusions.filter{blocked(e,it)}.forEach { x ->
            val stop=x.endMs?:Long.MAX_VALUE
            spans=spans.flatMap { s ->
                if(x.startMs>=s.end||stop<=s.start)listOf(s)
                else buildList { if(x.startMs>s.start)add(Span(s.start,minOf(x.startMs,s.end)));if(stop<s.end)add(Span(maxOf(stop,s.start),s.end)) }
            }
        }
        return spans
    }
    fun contribution(day:DayView):AchievementDayContribution {
        require(day.zoneId==policy.archiveZone){"成就归档时区不一致"}
        val cutoff=minOf(day.asOfMs,day.windowEndMs)
        val valid=day.episodes.distinctBy{it.id}.filter { e ->
            e.assertion !in setOf(Assertion.PLANNED,Assertion.UNKNOWN)&&e.evidence.isNotEmpty()&&
                e.packageName !in policy.excludedApps&&e.startMs<cutoff&&e.endMs>=day.windowStartMs&&
                e.qualityFlags.none{it in setOf("CLOCK_CONFLICT","MOCK","MOCK_LOCATION")}
        }.filter { e ->
            if(e.endMs>e.startMs)spans(e,day).isNotEmpty() else e.startMs>=day.windowStartMs&&
                policy.exclusions.none{blocked(e,it)&&e.startMs>=it.startMs&&e.startMs<(it.endMs?:Long.MAX_VALUE)}
        }
        // A saved daily total cannot be apportioned to a newly excluded interval.
        // Conservatively withhold that day's steps rather than invent a proportional count.
        val stepExcluded=policy.exclusions.any { x ->
            x.packageName==null&&(x.source==null||x.source in setOf(Source.STEPS,Source.HEALTH))&&
                x.startMs<cutoff&&(x.endMs?:Long.MAX_VALUE)>day.windowStartMs
        }
        val steps=day.metrics.firstOrNull{it.key=="steps"}?.value?.takeIf{it>=0&&!stepExcluded}
        val qualified=valid.filter { e ->
            when(e.kind){
                "PHOTO"->e.photoUris.any{it.isNotBlank()}
                "APP","STAY","MEDIA","SLEEP"->spans(e,day).sumOf{it.duration}>=10*60_000L
                "EXERCISE"->spans(e,day).sumOf{it.duration}>=5*60_000L
                else->false
            }
        }
        val exercise=valid.filter{it.kind=="EXERCISE"&&it.assertion in setOf(Assertion.RECORDED,Assertion.CONFIRMED)&&it.evidence.any{v->v.source==Source.HEALTH}}
        val apps=valid.filter{it.kind=="APP"&&it.assertion in setOf(Assertion.RECORDED,Assertion.CONFIRMED)&&it.evidence.any{v->v.source==Source.USAGE}}
        return AchievementDayContribution(day.dayDate,day.zoneId,qualified.isNotEmpty()||(steps?:0)>0,steps,
            union(exercise.flatMap{spans(it,day)}).sumOf{it.duration},
            union(apps.filter{it.packageName in settings.readingPackages}.flatMap{spans(it,day)}).sumOf{it.duration},
            exercise.isNotEmpty(),apps.isNotEmpty(),
            valid.filter{it.kind=="PHOTO"&&it.assertion in setOf(Assertion.RECORDED,Assertion.CONFIRMED)&&it.evidence.any{v->v.source==Source.PHOTO}}
                .flatMap{it.photoUris}.filter{it.isNotBlank()}.distinct(),
            valid.filter{it.isFavorite}.map{it.id},valid.isNotEmpty(),
            valid.filter { it.kind=="STAY"&&it.assertion==Assertion.CONFIRMED&&it.evidence.any{v->v.source==Source.LOCATION}&&
                it.lat?.let{lat->lat.isFinite()&&lat in -90.0..90.0}==true&&it.lon?.let{lon->lon.isFinite()&&lon in -180.0..180.0}==true&&spans(it,day).sumOf{s->s.duration}>=600_000L
            }.map { AchievementPlace(it.lat!!,it.lon!!) })
    }
    fun accept(day:DayView)=accept(contribution(day))
    fun accept(day:AchievementDayContribution) {
        require(day.zone==policy.archiveZone){"成就归档时区不一致"}
        val date=LocalDate.parse(day.date)
        require(previousDate==null||date>previousDate){"成就日输入需按日期去重后升序排列"}
        previousDate=date
        day.steps?.let { steps->observed+=AchievementMetric.STEPS;values[AchievementMetric.STEPS]=values.getValue(AchievementMetric.STEPS)+steps }
        if(day.recorded){
            values[AchievementMetric.DAYS]=values.getValue(AchievementMetric.DAYS)+1
            observed+=AchievementMetric.DAYS;observed+=AchievementMetric.STREAK
            streak=if(previousRecorded?.plusDays(1)==date)streak+1 else 1
            previousRecorded=date;values[AchievementMetric.STREAK]=maxOf(values.getValue(AchievementMetric.STREAK),streak)
        }
        if(day.exerciseObserved)observed+=AchievementMetric.EXERCISE
        exerciseMs+=day.exerciseMs
        values[AchievementMetric.EXERCISE]=exerciseMs/60_000L
        if(day.readingObserved)observed+=AchievementMetric.READING
        readingMs+=day.readingMs
        values[AchievementMetric.READING]=readingMs/60_000L
        photos.addAll(day.photoIds)
        if(photos.isNotEmpty())observed+=AchievementMetric.PHOTOS
        values[AchievementMetric.PHOTOS]=photos.size.toLong()
        favorites.addAll(day.favoriteIds)
        if(day.favoriteObserved)observed+=AchievementMetric.FAVORITES
        values[AchievementMetric.FAVORITES]=favorites.size.toLong()
        day.places.forEach { e ->
            val point=e.lat to e.lon
            if(places.none{distance(it,point)<300.0})places+=point
        }
        if(places.isNotEmpty())observed+=AchievementMetric.PLACES
        values[AchievementMetric.PLACES]=places.size.toLong()
        goals.forEach { g -> if(values.getValue(g.metric)>=g.target)earned.putIfAbsent(g.id,date.toString()) }
    }
    fun snapshot(memory:AchievementMemory=AchievementMemory(),fromDate:String="",toDate:String="",demo:Boolean=false):AchievementSnapshot {
        val items=goals.map { AchievementProgress(it,values.getValue(it.metric),earned[it.id],it.metric in observed) }
        return AchievementSnapshot(items,settings,if(demo)emptySet() else earned.keys-memory.seenIds,fromDate,toDate,demo)
    }
    private fun distance(a:Pair<Double,Double>,b:Pair<Double,Double>):Double {
        val rad=PI/180;val dlat=(b.first-a.first)*rad;val dlon=(b.second-a.second)*rad
        val h=sin(dlat/2).pow(2)+cos(a.first*rad)*cos(b.first*rad)*sin(dlon/2).pow(2)
        return 12_742_000*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
}

fun achievementSnapshot(days:List<DayView>,settings:AchievementSettings=AchievementSettings(),policy:Policy,
    memory:AchievementMemory=AchievementMemory(),demo:Boolean=false):AchievementSnapshot {
    val unique=days.filter{it.zoneId==policy.archiveZone}.groupBy{it.dayDate}.values.map{rows->rows.maxWith(compareBy({it.revision},{it.asOfMs}))}.sortedBy{it.dayDate}
    val accumulator=AchievementAccumulator(settings,policy)
    unique.forEach{accumulator.accept(it)}
    return accumulator.snapshot(memory,unique.firstOrNull()?.dayDate.orEmpty(),unique.lastOrNull()?.dayDate.orEmpty(),demo)
}

package com.personal.wo.collector

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.hardware.*
import android.location.*
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.personal.wo.*
import com.personal.wo.core.*
import kotlinx.coroutines.launch
import java.time.*

class RecordingService:Service(),SensorEventListener,LocationListener {
    private lateinit var thread:HandlerThread
    private lateinit var handler:Handler
    private lateinit var location:LocationManager
    private lateinit var sensors:SensorManager
    private val baseline=StepBaseline()
    private var pendingSteps=0L;private var stepStart=0L;private var stepEnd=0L
    private var lastPoint:Location?=null;private var movementAt=0L;private var lastGnss=0L
    private var positionMode="";private var registered=false;private var startMs=0L
    private var observer:ContentObserver?=null
    private var powerMode="NORMAL";private var recoverySince=0L
    private var candidateSince=0L;private var candidateFixes=0
    private val gpsListener=object:LocationListener {
        override fun onLocationChanged(fix:Location) { guardedSensors { onFix(fix);location.removeUpdates(this) } }
        override fun onProviderEnabled(provider:String)=Unit
        override fun onProviderDisabled(provider:String)=Unit
    }
    private val motion=object:TriggerEventListener() { override fun onTrigger(event:TriggerEvent?) {
        movementAt=System.currentTimeMillis();handler.post { guardedSensors { adjust();maybeGnss();registerMotion() } }
    } }
    override fun onCreate() {
        super.onCreate()
        recoverableCall { startRecording() }.onFailure { wo().diagnostics.record("记录服务启动",it);stopSelf() }
    }
    private fun startRecording() {
        startMs=System.currentTimeMillis();location=getSystemService(LocationManager::class.java);sensors=getSystemService(SensorManager::class.java)
        val p=wo().currentGraph?.repo?.cachedPolicy
        val loc=Capabilities.location(this)&&location.isLocationEnabled&&(p==null||Source.LOCATION in p.enabledSources)
        val steps=Capabilities.steps(this)&&(p==null||Source.STEPS in p.enabledSources)
        if(!loc&&!steps) { stopSelf();return }
        var types=if(loc) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        if(steps&&Build.VERSION.SDK_INT>=34)types=types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        try { ServiceCompat.startForeground(this,101,notification(this,"我正在整理今天",true),types) }
        catch(e:RuntimeException) { wo().diagnostics.record("前台服务权限",e);stopSelf();return }
        getSystemService(NotificationManager::class.java).cancel(102)
        thread=HandlerThread("wo-sensors").apply { start() };handler=Handler(thread.looper)
        wo().scope.launch { recoverableTask { val g=wo().graph();if(!g.repo.cachedPolicy.requested||g.repo.cachedPolicy.paused) { stopSelf();return@recoverableTask };handler.post { guardedSensors { register(steps);adjust();audit() } };WorkScheduling.schedule(this@RecordingService) }.onFailure { wo().diagnostics.record("恢复记录服务",it);stopSelf() } }
    }
    private fun guardedSensors(action:()->Unit) { recoverableCall(action).onFailure { wo().diagnostics.record("传感器运行",it);stopSelf() } }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int=START_STICKY
    override fun onBind(intent:Intent?):IBinder?=null
    private fun register(stepEnabled:Boolean) {
        if(registered)return;registered=true
        if(stepEnabled) sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL,300_000_000,handler) }
        registerMotion()
        observer=object:ContentObserver(handler) { override fun onChange(selfChange:Boolean) { WorkScheduling.dayBuild(this@RecordingService);WorkScheduling.mediaPull(this@RecordingService) } }
        try { contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"),true,observer!!) } catch(e:SecurityException) { observer=null }
        if(Capabilities.location(this)&&Source.LOCATION in wo().currentGraph!!.repo.cachedPolicy.enabledSources) try { location.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,60_000L,0f,this,thread.looper) } catch(e:SecurityException) { permissionLost() }
    }
    private fun registerMotion() { sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let { runCatching { sensors.requestTriggerSensor(motion,it) } } }
    private fun resourceMode():String {
        val battery=getSystemService(BatteryManager::class.java);val power=getSystemService(PowerManager::class.java)
        val level=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);val config=wo().currentGraph?.config ?: EngineConfig()
        val thermal=recoverableCall { power.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_SEVERE)
        val candidate=if((level in 0..config.passiveBattery&&!battery.isCharging)||thermal>=PowerManager.THERMAL_STATUS_SEVERE) "PASSIVE"
        else if((level in 0..config.lowBattery&&!battery.isCharging)||power.isPowerSaveMode||thermal>=PowerManager.THERMAL_STATUS_MODERATE) "ECONOMY" else "NORMAL"
        if(candidate!="NORMAL") { powerMode=candidate;recoverySince=0;return powerMode }
        if(powerMode!="NORMAL") {
            if(!battery.isCharging && level<=config.recoveryBattery) { recoverySince=0;return powerMode }
            if(recoverySince==0L)recoverySince=SystemClock.elapsedRealtime()
            if(SystemClock.elapsedRealtime()-recoverySince<10*60_000L)return powerMode
        }
        powerMode="NORMAL";return powerMode
    }
    private fun adjust() {
        if(!Capabilities.location(this)||!location.isLocationEnabled)return
        val p=wo().currentGraph?.repo?.cachedPolicy ?: return
        if(Source.LOCATION !in p.enabledSources) { location.removeUpdates(this);return }
        val resource=resourceMode();val moving=System.currentTimeMillis()-movementAt<10*60_000
        val candidate=lastPoint==null || (candidateSince>0&&System.currentTimeMillis()-candidateSince<12*60_000L&&(candidateFixes<3||System.currentTimeMillis()-candidateSince<10*60_000L))
        val next=if(resource!="NORMAL")resource else if(candidate) "CANDIDATE" else if(moving) "MOVING" else "STAY"
        if(next==positionMode)return;positionMode=next
        location.removeUpdates(this)
        try { location.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,60_000L,0f,this,thread.looper) } catch(e:SecurityException) { permissionLost() }
        if(next=="PASSIVE") { location.removeUpdates(gpsListener);return }
        if(location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            val interval=when(next) { "ECONOMY"->30*60_000L;"MOVING"->2*60_000L;"CANDIDATE"->3*60_000L;else->15*60_000L }
            val config=wo().currentGraph!!.config
            val distance=when(next) { "MOVING"->100f;"CANDIDATE"->config.candidateMinDistanceM;else->config.stayMinDistanceM }
            try { location.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,interval,distance,this,thread.looper) } catch(e:SecurityException) { permissionLost() }
        }
    }
    private fun maybeGnss() {
        val g=wo().currentGraph ?: return;val now=System.currentTimeMillis();val config=g.config
        if(resourceMode()!="NORMAL" || !Capabilities.granted(this,Manifest.permission.ACCESS_FINE_LOCATION) || Source.LOCATION !in g.repo.cachedPolicy.enabledSources)return
        if(now-movementAt>20*60_000 && lastPoint!=null)return
        if(lastPoint!=null && now-lastPoint!!.time<5*60_000)return
        if(!location.isProviderEnabled(LocationManager.GPS_PROVIDER))return
        val budget=getSharedPreferences("gnss-budget",0);val hour=now/3_600_000L;val date=Instant.ofEpochMilli(now).atZone(ZoneId.of(g.repo.cachedPolicy.archiveZone)).toLocalDate().toString()
        val hours=if(budget.getLong("hour",-1)==hour)budget.getInt("hourSeconds",0) else 0
        val days=if(budget.getString("date",null)==date)budget.getInt("daySeconds",0) else 0
        val boot=Settings.Global.getInt(contentResolver,Settings.Global.BOOT_COUNT,0)
        val lastElapsed=if(budget.getInt("boot",-1)==boot)budget.getLong("lastElapsed",-1_000_000) else -1_000_000
        if(SystemClock.elapsedRealtime()-lastElapsed<config.gnssCooldownMinutes*60_000L || hours+config.gnssBurstSeconds>config.gnssHourlySeconds || days+config.gnssBurstSeconds>config.gnssDailySeconds)return
        budget.edit().putLong("hour",hour).putInt("hourSeconds",hours+config.gnssBurstSeconds).putString("date",date).putInt("daySeconds",days+config.gnssBurstSeconds).putInt("boot",boot).putLong("lastElapsed",SystemClock.elapsedRealtime()).apply()
        try { location.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,20f,gpsListener,thread.looper)
            handler.postDelayed({ location.removeUpdates(gpsListener) },config.gnssBurstSeconds*1000L) } catch(e:SecurityException) { permissionLost() }
    }
    private fun permissionLost() { wo().scope.launch { val r=wo().graph().repo;r.state(r.source(Source.LOCATION).copy(authorization="DENIED",runtime="STOPPED",detail="P001:定位权限已撤回")) } }
    override fun onLocationChanged(fix:Location) { guardedSensors { onFix(fix) } }
    private fun onFix(fix:Location) {
        val g=wo().currentGraph ?: return;val now=System.currentTimeMillis()
        if(fix.latitude !in -90.0..90.0||fix.longitude !in -180.0..180.0||!fix.hasAccuracy()||fix.accuracy<=0||fix.time>now+300_000)return
        val previous=lastPoint
        if(previous==null || distanceM(previous.latitude,previous.longitude,fix.latitude,fix.longitude)>maxOf(150.0,fix.accuracy*2.0)) { candidateSince=now;candidateFixes=0 }
        if(fix.accuracy<=200)candidateFixes++
        if(previous!=null) {
            if(fix.elapsedRealtimeNanos==previous.elapsedRealtimeNanos)return
            if(distanceM(previous.latitude,previous.longitude,fix.latitude,fix.longitude)>maxOf(150.0,previous.accuracy+fix.accuracy.toDouble())) movementAt=now
            if(fix.time>previous.time && fix.time-previous.time<=g.config.stayMaxGapMinutes*60_000L && previous.accuracy<=200&&fix.accuracy<=200)
                wo().scope.launch { g.repo.coverage(Source.LOCATION,previous.time,fix.time,true,"有效位置支撑区间；不代表人始终携带手机") }
        }
        lastPoint=fix
        val key=g.vault.sourceKey("position",fix.time,fix.provider,fix.elapsedRealtimeNanos)
        val mock=if(Build.VERSION.SDK_INT>=31)fix.isMock else fix.isFromMockProvider
        g.offer(Observation(stableId(Source.LOCATION,key),Source.LOCATION,key,Kind.POSITION,fix.time,receivedMs=now,elapsedRealtimeNs=fix.elapsedRealtimeNanos,
            quality=if(mock) listOf("MOCK_LOCATION") else emptyList(),payload=Payload(lat=fix.latitude,lon=fix.longitude,accuracyM=fix.accuracy.toDouble(),provider=fix.provider.orEmpty(),mock=mock)))
        adjust()
    }
    override fun onProviderEnabled(provider:String) { guardedSensors { adjust() } }
    override fun onProviderDisabled(provider:String) { guardedSensors { positionMode="";adjust() } }
    override fun onSensorChanged(event:SensorEvent) {
        guardedSensors { acceptSteps(event) }
    }
    private fun acceptSteps(event:SensorEvent) {
        val now=System.currentTimeMillis();val boot=Settings.Global.getInt(contentResolver,Settings.Global.BOOT_COUNT,0).toString()
        val delta=baseline.update(event.values[0].toLong(),boot,now) ?: return
        if(delta.second<=0)return
        movementAt=now
        if(pendingSteps==0L)stepStart=delta.first.start
        stepEnd=delta.first.end;pendingSteps+=delta.second
        if(stepEnd-stepStart>=60_000)flushSteps()
        adjust()
    }
    private fun flushSteps() {
        val g=wo().currentGraph ?: return
        if(pendingSteps==0L)return
        val key=g.vault.sourceKey("steps",stepStart,stepEnd,pendingSteps)
        g.offer(Observation(stableId(Source.STEPS,key),Source.STEPS,key,Kind.STEP_DELTA,stepStart,stepEnd,System.currentTimeMillis(),bootId=Settings.Global.getInt(contentResolver,Settings.Global.BOOT_COUNT,0).toString(),payload=Payload(steps=pendingSteps)))
        pendingSteps=0
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    private fun audit() {
        val g=wo().currentGraph ?: return
        if(!g.repo.cachedPolicy.requested||g.repo.cachedPolicy.paused) { stopSelf();return }
        flushSteps();adjust();maybeGnss()
        wo().scope.launch { recoverableTask { g.collector.probe();g.repo.heartbeat() }.onFailure { wo().diagnostics.record("来源检查",it) } }
        getSystemService(NotificationManager::class.java).notify(101,notification(this,if(resourceMode()=="NORMAL") "我正在整理今天" else "我正在低功耗整理今天",true))
        handler.postDelayed({ guardedSensors { audit() } },300_000)
    }
    override fun onDestroy() {
        if(::handler.isInitialized) { handler.removeCallbacksAndMessages(null);flushSteps();sensors.unregisterListener(this);sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let { sensors.cancelTriggerSensor(motion,it) };runCatching { location.removeUpdates(this);location.removeUpdates(gpsListener) };observer?.let { contentResolver.unregisterContentObserver(it) };thread.quitSafely() }
        super.onDestroy()
    }
    companion object {
        fun notification(c:Context,text:String,pause:Boolean):Notification {
            val manager=c.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("recording","记录状态",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null);enableVibration(false);setShowBadge(false) })
            val view=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).putExtra("resume",!pause),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b=NotificationCompat.Builder(c,"recording").setSmallIcon(R.drawable.ic_wo).setContentTitle(text).setContentText(if(pause) "本地记录 · 点此查看今天" else "点此恢复位置与步数记录").setContentIntent(view).setSilent(true).setOngoing(pause).setOnlyAlertOnce(true)
            if(pause)b.addAction(0,"暂停",PendingIntent.getBroadcast(c,1,Intent(c,PauseReceiver::class.java),PendingIntent.FLAG_IMMUTABLE))
            return b.build()
        }
        fun recoveryNotification(c:Context) { runCatching { c.getSystemService(NotificationManager::class.java).notify(102,notification(c,"点此继续记录",false)) } }
        fun start(c:Context) { if(Capabilities.location(c)||Capabilities.steps(c)) runCatching { androidx.core.content.ContextCompat.startForegroundService(c,Intent(c,RecordingService::class.java)) } }
    }
}

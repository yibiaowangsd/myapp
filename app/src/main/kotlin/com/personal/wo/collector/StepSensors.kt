package com.personal.wo.collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

data class StepCandidate<T>(val sensor: T, val counter: Boolean, val label: String)

/** A failed batched registration must not be mistaken for a connected source. */
fun <T> registerStepCandidates(candidates: List<StepCandidate<T>>, register: (T, Int) -> Boolean): StepCandidate<T>? {
    for(candidate in candidates.sortedBy { !it.counter }) {
        val latencies = if(candidate.counter) listOf(10_000_000, 0) else listOf(0)
        for(latency in latencies) if(runCatching { register(candidate.sensor, latency) }.getOrDefault(false)) return candidate
    }
    return null
}

object StepSensors {
    @Volatile var connection = "STOPPED"
        private set
    @Volatile var detail = "手机计步独立工作，无需连接 Health Connect"
        private set
    @Volatile var lastEventMs: Long? = null
        private set

    fun candidates(manager: SensorManager?): List<StepCandidate<Sensor>> {
        if(manager == null) return emptyList()
        return listOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR).flatMap { type ->
            // Include wake-up and non-default sensors exposed by vendor firmware.
            (listOfNotNull(runCatching { manager.getDefaultSensor(type, true) }.getOrNull(),
                runCatching { manager.getDefaultSensor(type, false) }.getOrNull()) +
                runCatching { manager.getSensorList(type) }.getOrDefault(emptyList()))
                .distinct().sortedBy { !it.isWakeUpSensor }
                .map { StepCandidate(it, type == Sensor.TYPE_STEP_COUNTER, it.name.orEmpty()) }
        }
    }
    fun supported(c: Context) = candidates(c.getSystemService(SensorManager::class.java)).isNotEmpty()
    fun register(manager: SensorManager, listener: SensorEventListener, handler: Handler, detectorOnly:Boolean=false): StepCandidate<Sensor>? {
        lastEventMs = null
        val choice = registerStepCandidates(candidates(manager).filter { !detectorOnly||!it.counter }) { sensor, latency ->
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, latency, handler)
        }
        connection = if(choice == null) "ERROR" else "WAITING"
        detail = if(choice == null) "计步传感器未能连接，请检查身体活动权限后点重连"
            else if(choice.counter) "计步器已连接，走动后自动更新；从本次启用开始累计"
            else "已连接硬件单步检测器，走动后自动更新；从本次启用开始累计"
        return choice
    }
    fun received(at: Long) { lastEventMs = at; connection = "RUNNING"; detail = "手机计步正常，从启用记录后的步数累计" }
    fun stopped() { connection = "STOPPED"; detail = "手机计步尚未启动" }
}

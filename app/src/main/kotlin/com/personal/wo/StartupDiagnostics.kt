package com.personal.wo

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

class StartupDiagnostics(private val context: Context) {
    private val reportFile get() = File(context.noBackupFilesDir, "startup-diagnostic.txt")

    fun record(stage: String, error: Throwable): String {
        val report = buildString {
            appendLine("我 · 启动诊断 S001")
            appendLine("版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统：${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("时间：${Instant.now()}")
            appendLine("阶段：$stage")
            append(failureDetails(error))
        }
        recoverableCall {
            synchronized(this) {
                val tmp = File(reportFile.parentFile, "startup-diagnostic.tmp")
                tmp.writeText(report)
                check(tmp.renameTo(reportFile))
            }
        }
        return report
    }

    fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (error !is VirtualMachineError && error !is ThreadDeath) {
                recoverableCall { record("未处理异常 / ${thread.name}", error) }
            }
            // Recording a failure must never turn into an endless restart loop.
            if (previous != null) previous.uncaughtException(thread, error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

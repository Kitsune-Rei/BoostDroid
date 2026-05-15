package com.boostdroid.app

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.io.IOException

object ProcReader {

    fun readProcessRamMb(pid: Int): Int {
        // Try VmRSS first (physical RAM)
        return try {
            val lines = File("/proc/$pid/status").readLines()
            val vmRss = lines.firstOrNull { it.startsWith("VmRSS:") }
                ?.trim()?.split("\\s+".toRegex())?.getOrNull(1)
                ?.toLongOrNull() ?: 0L
            (vmRss / 1024).toInt()
        } catch (e: IOException) { 0 }
          catch (e: SecurityException) { 0 }
          catch (e: Exception) { 0 }
    }

    fun getAppState(context: Context, packageName: String): AppState {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val proc = am.runningAppProcesses?.firstOrNull { 
            it.processName.startsWith(packageName) 
        } ?: return AppState.NOT_RUNNING
        
        return when {
            proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> 
                AppState.ACTIVE
            proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> 
                AppState.STUCK
            proc.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> 
                AppState.CACHED
            else -> AppState.NOT_RUNNING
        }
    }

    fun isStorageIoBusy(): Boolean {
        // Read /proc/diskstats twice with 200ms gap, check write rate
        val s1 = readDiskStatsSectors() ?: return false
        Thread.sleep(200)
        val s2 = readDiskStatsSectors() ?: return false
        val writeKBs = ((s2 - s1) * 512 / 1024) * 5 // * 5 because 200ms gap = 1/5 second
        return writeKBs > 1000
    }

    private fun readDiskStatsSectors(): Long? {
        return try {
            File("/proc/diskstats").readLines()
                .firstOrNull { 
                    it.trim().split("\\s+".toRegex()).getOrNull(2) == "mmcblk0" 
                }
                ?.trim()?.split("\\s+".toRegex())?.getOrNull(9)?.toLongOrNull()
        } catch (e: Exception) { null }
    }

    fun readMemAvailableMb(context: Context): Int {
        return try {
            File("/proc/meminfo")
                .readLines()
                .firstOrNull { it.startsWith("MemAvailable:") }
                ?.trim()
                ?.split("\\s+".toRegex())
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?.div(1024L)
                ?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }
}

package com.boostdroid.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

data class ReviveResult(
    val strategy: ReviveStrategy,
    val memFreedMb: Int,
    val killedCount: Int,
    val ioWasBusy: Boolean,
    val targetWasKillable: Boolean,
    val launched: Boolean,
    val message: String
)

enum class ReviveStrategy {
    MEMORY_RELIEF,      // freed RAM around target app
    IO_RELIEF,          // waited for I/O to calm down
    MANUAL_REQUIRED,    // app is stuck, user must force-stop manually
    ALREADY_RUNNING     // app was fine, just launched
}

object QuickReviveManager {

    fun revive(context: Context, packageName: String): ReviveResult {

        // STEP 1: Diagnose the situation
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        // Check target app's current state
        val targetProcess = am.runningAppProcesses?.firstOrNull { 
            it.processName.startsWith(packageName) 
        }
        val targetImportance = targetProcess?.importance ?: -1
        
        // Check memory pressure
        val memBefore = ProcReader.readMemAvailableMb(context) // from /proc/meminfo MemAvailable
        val isMemCritical = memBefore < 400  // less than 400MB available = critical
        
        // Check I/O pressure  
        val ioBusy = ProcReader.isStorageIoBusy()  // from /proc/diskstats, write > 1000 KB/s
        
        // STEP 2: Apply the correct strategy
        
        if (targetImportance > 0 && 
            targetImportance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) {
            // App process exists but is not cached — it may be stuck
            // We CANNOT kill it. Tell user to do it manually.
            val pm = context.packageManager
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) { packageName }
            
            return ReviveResult(
                strategy = ReviveStrategy.MANUAL_REQUIRED,
                memFreedMb = 0,
                killedCount = 0,
                ioWasBusy = ioBusy,
                targetWasKillable = false,
                launched = false,
                message = context.getString(R.string.revive_stuck_msg, appName)
            )
        }
        
        // STEP 3: Memory relief — kill everything EXCEPT target app
        var killedCount = 0
        val processes = am.runningAppProcesses ?: emptyList()
        val pm = context.packageManager
        val myPkg = context.packageName
        val myPid = android.os.Process.myPid()
        
        val targets = processes.filter { proc ->
            proc.pid != myPid &&
            proc.processName != myPkg &&
            !proc.processName.startsWith(packageName) && // NEVER kill target app
            proc.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
        }.sortedByDescending { it.importance } // kill least important first
        
        targets.chunked(3).forEach { batch ->
            batch.forEach { proc ->
                try {
                    // Only kill user apps, not system processes
                    val info = pm.getApplicationInfo(proc.processName.split(":")[0], 0)
                    val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!isSystem) {
                        am.killBackgroundProcesses(proc.processName)
                        killedCount++
                    }
                } catch (e: Exception) { /* system process, skip */ }
            }
            Thread.sleep(150) // eMMC-friendly batching
        }
        
        // STEP 4: Force GC
        Runtime.getRuntime().gc()
        System.gc()
        Thread.sleep(300)
        Runtime.getRuntime().gc()
        
        // STEP 5: If I/O was busy, wait for it to calm down
        var ioWaitMs = 0
        if (ioBusy) {
            repeat(10) { // wait up to 2 seconds
                Thread.sleep(200)
                ioWaitMs += 200
                if (!ProcReader.isStorageIoBusy()) return@repeat
            }
        }
        
        // STEP 6: Measure actual freed RAM
        Thread.sleep(500) // let OS reclaim memory
        val memAfter = ProcReader.readMemAvailableMb(context)
        val freed = (memAfter - memBefore).coerceAtLeast(0)
        
        // STEP 7: Only launch if we have enough RAM
        val canLaunch = memAfter >= 250 // minimum viable free RAM for a cold start
        var launched = false
        
        if (canLaunch) {
            try {
                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage(packageName)
                    ?.apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        )
                    }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    launched = true
                }
            } catch (e: Exception) { launched = false }
        }
        
        val message = buildString {
            if (freed > 0) append(context.getString(R.string.revive_ram_freed, freed))
            if (killedCount > 0) append(context.getString(R.string.revive_apps_cleaned, killedCount))
            if (ioBusy) append(context.getString(R.string.revive_io_wait))
            if (!canLaunch) append(context.getString(R.string.revive_ram_insufficient))
            else if (launched) append(context.getString(R.string.revive_app_launched))
        }
        
        return ReviveResult(
            strategy = ReviveStrategy.MEMORY_RELIEF,
            memFreedMb = freed,
            killedCount = killedCount,
            ioWasBusy = ioBusy,
            targetWasKillable = targetImportance < 0,
            launched = launched,
            message = message
        )
    }
}
